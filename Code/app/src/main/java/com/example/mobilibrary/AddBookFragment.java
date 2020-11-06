package com.example.mobilibrary;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.mobilibrary.DatabaseController.BookService;
import com.example.mobilibrary.DatabaseController.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;


public class AddBookFragment extends AppCompatActivity implements Serializable {
    private EditText newTitle;
    private EditText newAuthor;
    private EditText newIsbn;
    private ImageView newImage;
    private Uri imageUri;
    private Button confirmButton;
    private FloatingActionButton backButton;
    private FloatingActionButton cameraButton;

    private RequestQueue mRequestQueue;
    private FirebaseFirestore db;
    private BookService bookService;
    private Context context;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_add_book_fragment);
        newTitle = findViewById(R.id.book_title);
        newAuthor = findViewById(R.id.book_author);
        newIsbn = findViewById(R.id.book_isbn);
        newImage = findViewById(R.id.book_image);
        confirmButton = findViewById(R.id.confirm_button);
        backButton = findViewById(R.id.back_button);
        cameraButton = findViewById(R.id.camera_button);

        mRequestQueue = Volley.newRequestQueue(this);

        bookService = BookService.getInstance();
        context = getApplicationContext();

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                PackageManager.PERMISSION_GRANTED); //Request permission to use Camera

        /*
          If user wants to cancel add process, can press back button to cancel
         */
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        /*
          confirmButton first check if the three important fields (title,author and ISBN) are filled
          and are valid. The it will add an available status to book and if there is an image in
          imageView then will convert it to a byte array (so it can be serialized). It will check who
          is the current user using the onCallBack interface and set that as the book owner and creates
          the book object and sends it to myBooks
         */
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String bookTitle = newTitle.getText().toString();
                final String bookAuthor = newAuthor.getText().toString();
                final String bookISBN = newIsbn.getText().toString();
                if (checkInputs(bookTitle, bookAuthor, bookISBN)) {
                    currentUser(new Callback() {
                        @Override
                        public void onCallback(User user) {
                            User bookOwner = user;
                            String bookStatus = "available";
                            Uri bookImage = getImageUri();
                            Book newBook = new Book(bookTitle,bookISBN,bookAuthor,bookStatus,bookImage,bookOwner);
                            bookService.addBook(context, newBook);
                            if (getImageUri() != null){
                                bookService.uploadImage(newBook.getFirestoreID(), getImageUri(), new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Picasso.with(context).load(getImageUri()).into(newImage);
                                    }
                                }, new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(AddBookFragment.this, "Failed to add image.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            Intent returnIntent = new Intent();
                            returnIntent.putExtra("new book", newBook);
                            setResult(RESULT_OK, returnIntent);
                            finish();
                        }
                    });

                }
            }
        });

        /*
          opens the scan button method to start scanning ISBN
         */
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                ScanButton(v);
            }
        });

        /*
            If imageView is selected, it will open the camera intent and start the device
            camera.
         */
        newImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                imageUri = getImageUri();
                camera_intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                int pic_id = 2;
                startActivityForResult(camera_intent, pic_id);
            }
        });
    }

    /**
     * Scan button will open the device camera scanner and will allow
     * user to scan
     *
     * @param view
     */
    public void ScanButton(View view) { //When camera button is clicked
        IntentIntegrator intentIntegrator = new IntentIntegrator(this);
        intentIntegrator.initiateScan();
    }

    /**
     * If requestCode is 2, we are adding image to the imageView (image of book) and
     * setting it. Else, we are taking the image from the scanner, first check if the
     * results are not null (if so, error message will appear), then if device is connected
     * to the internet will parse the data.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2) {
            Bundle cameraBundle = data.getExtras();
            Uri image = (Uri) cameraBundle.get(MediaStore.EXTRA_OUTPUT);
            if(resultCode == Activity.RESULT_OK) {
                try {
                    // Setting image on image view using Bitmap
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                    newImage.setImageBitmap(bitmap);
                }

                catch (IOException e) {
                    // Log the exception
                    e.printStackTrace();
                }
            }
        } else {
            IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            if (intentResult != null) { //scanner got a result
                if (intentResult.getContents() == null) { //scanner worked, but was not able to get data
                    System.out.println("scanner worked, but not able to get data");
                    Toast toast = Toast.makeText(this, "Unable to obtain data from barcode",
                            Toast.LENGTH_SHORT); //used ot display error message
                    toast.show();
                } else {//got ISBN
                    //Use the ISBN to search through Google Books API to find the author, and title.
                    String isbn = intentResult.getContents();
                    newIsbn.setText(isbn);

                    //Check if connected to internet
                    boolean isConnected = isNetworkAvailable();
                    if (!isConnected) {
                        System.out.println("Check Internet Connection");
                        Toast.makeText(getApplicationContext(), "Please check Internet connection",
                                Toast.LENGTH_LONG).show(); //Popup message for user
                        return;
                    }

                    final String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:"; //base url
                    Uri uri = Uri.parse(url + isbn);
                    Uri.Builder builder = uri.buildUpon();

                    parseJson(builder.toString()); //get results from webpage
                }
            }
        }
    }

    /**
     * will take a string key and create a new parseJson, which will take the data from the
     * scanner, find the relevant book information (title, author and isbn) and will set the
     * corresponding fields, or it will return an error message.
     *
     * @param key
     */
    private void parseJson(String key) {

        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, key.toString(),
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        String title = "";
                        String author = "";

                        try {

                            JSONArray items = response.getJSONArray("items");
                            JSONObject item = items.getJSONObject(0);
                            JSONObject volumeInfo = item.getJSONObject("volumeInfo");

                            try {
                                title = volumeInfo.getString("title");
                                System.out.println("title: " + title);
                                newTitle.setText(title);

                                JSONArray authors = volumeInfo.getJSONArray("authors");
                                if (authors.length() == 1) {
                                    author = authors.getString(0);
                                } else { //if there are multiple authors
                                    author = authors.getString(0) + "," + authors.getString(1);
                                }
                                System.out.println("author: " + author);
                                newAuthor.setText(author);

                            } catch (Exception e) { //the book info in database does not contain a title or author
                                if (title == "") {
                                    newTitle.setText("Title not found");
                                } else {
                                    newAuthor.setText("Author not found");
                                }
                            }

                        } catch (JSONException e) { //error trying to get database info
                            e.printStackTrace();
                            newTitle.setText("Title not found.");
                            newAuthor.setText("Author not found.");
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        mRequestQueue.add(request);
    }

    /**
     * Checks to see if network connection is available (needed for
     *
     * @return info
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        return info != null && info.isConnected();

    }

    /**
     * Checks to see if the title, author and ISBN text fields are empty.
     * As well as if the ISBN is the correct length. If not valid, will
     * sent setError message, if valid will return a true boolean.
     *
     * @param title
     * @param Author
     * @param ISBN
     * @return boolean
     */
    public Boolean checkInputs(String title, String Author, String ISBN) {
        boolean inputsGood = true;
        if (title.isEmpty()) {
            newTitle.setError("Please insert book title!");
            inputsGood = false;
        }
        if (Author.isEmpty()) {
            newAuthor.setError("Please insert book author!");
            inputsGood = false;
        }
        if (ISBN.isEmpty() || ISBN.length() < 13) {
            newIsbn.setError("Please insert book ISBN!");
            inputsGood = false;
        }
        return inputsGood;
    }

    /**
     * used to check if imageUri is null
     *
     * @return Uri
     */

    public Uri getImageUri() {
        return imageUri;
    }

    /**
     * currentUser uses the current instance of the firebase auth to get the information of the
     * current user and create a User based on it. Because onComplete is asynchronous (so the info
     * won't arrive until after the code completes) we need to use onCallBack interface. It will
     * take the info and allow the information to be used (without null).
     *
     * @param cbh
     */
    public void currentUser(final Callback cbh) {
        final FirebaseUser userInfo = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();
        db.collection("Users").whereEqualTo("email", userInfo.getEmail()).get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (DocumentSnapshot document : task.getResult()) {
                                String username = document.get("username").toString();
                                String email = userInfo.getEmail();
                                String name = document.get("name").toString();
                                String Phone = document.get("phoneNo").toString();
                                User currentUser = new User(username, email, name, Phone);
                                cbh.onCallback(currentUser);
                            }
                        }
                    }
                });
    }
}





