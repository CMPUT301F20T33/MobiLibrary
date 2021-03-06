package com.example.mobilibrary;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.mobilibrary.Activity.ProfileActivity;
import com.example.mobilibrary.DatabaseController.BookService;
import com.example.mobilibrary.DatabaseController.HandoverService;
import com.example.mobilibrary.DatabaseController.RequestService;
import com.example.mobilibrary.DatabaseController.User;
import com.example.mobilibrary.DatabaseController.aRequest;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.firestore.Query;

import com.google.firebase.firestore.FirebaseFirestoreException;

import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import java.util.HashMap;

/**
 * This class takes in a book and displays its details (Title, Author, Owner, ISBN and Status),
 * requests currently on the book, and, if available, the book's photograph.
 * Additionally, this class can toggle between displaying the book details and the list of requests on the book
 */
public class BookDetailsFragment extends AppCompatActivity {
    private TextView title;
    private TextView author;
    private TextView owner;
    private TextView ISBN;
    private TextView status;
    private TextView[] requestAssets;
    private ImageView photo;

    private Bitmap editBitMap = null;
    private ArrayList<aRequest> requestList;
    private RecyclerView reqView;
    private RecyclerView.Adapter requestAdapter;

    private FirebaseFirestore db;
    private BookService bookService;
    private RequestService requestService;
    private HandoverService handoverService;
    private Context context;

    private Button requestedButton;
    private Button requestButton;
    private Button lendButton;
    private Button borrowButton;
    private Button returnButton;
    private Button receiveButton;

    private boolean checkISBN = false;
    private String userName;
    private String bookFSID;
    private Book viewBook;

    /**
     * Creates the activity for viewing books and the requests on them, and the necessary logic to do so
     * @param SavedInstances The book to be viewed
     */
    @Override
    protected void onCreate (@Nullable Bundle SavedInstances) {
        super.onCreate(SavedInstances);
        setContentView(R.layout.layout_book_details_fragment);

        // set each variable to correct view
        title =  findViewById(R.id.view_title);
        author = findViewById(R.id.view_author);
        owner = findViewById(R.id.view_owner);
        status = findViewById(R.id.view_status);
        ISBN = findViewById(R.id.view_isbn);
        FloatingActionButton backButton = findViewById(R.id.back_to_books_button);
        ImageButton editButton = findViewById(R.id.edit_button);
        FloatingActionButton deleteButton = findViewById(R.id.delete_button);
        photo = findViewById(R.id.imageView);
        Button detailsBtn = findViewById(R.id.detailsBtn);
        Button requestsBtn = findViewById(R.id.reqBtn);
        reqView = findViewById(R.id.reqList);
        requestList = new ArrayList<>();

        requestedButton = findViewById(R.id.requested_button);
        requestButton = findViewById(R.id.request_button);
        returnButton = findViewById(R.id.return_button);
        receiveButton = findViewById(R.id.receive_button);
        lendButton = findViewById(R.id.lend_button);
        borrowButton = findViewById(R.id.borrow_button);

        //set all status changing buttons to be invisible
        requestButton.setVisibility(View.GONE);
        returnButton.setVisibility(View.GONE);
        receiveButton.setVisibility(View.GONE);
        requestedButton.setVisibility(View.GONE);
        lendButton.setVisibility(View.GONE);
        borrowButton.setVisibility(View.GONE);

        // set up firestore instance
        bookService = BookService.getInstance();
        requestService = RequestService.getInstance();
        handoverService = HandoverService.getInstance();
        context = getApplicationContext();
        db = FirebaseFirestore.getInstance();

        // set up permissions for scanning intent
        ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
                PackageManager.PERMISSION_GRANTED); //Request permission to use Camera

        // check that a book was passed to this activity, otherwise end the activity
        if (getIntent() == null) {
            finish();
        }
        viewBook = (Book) getIntent().getSerializableExtra("view book");
        bookFSID = viewBook.getFirestoreID();

        // fill fields with correct information from the passed book
        title.setText(viewBook.getTitle());
        author.setText(viewBook.getAuthor());
        owner.setText(viewBook.getOwner().getUsername());
        ISBN.setText(viewBook.getISBN());
        status.setText(viewBook.getStatus());

        convertImage(bookFSID);

        //get current user name and book owners name, check if they match
        CurrentUser currentUser = CurrentUser.getInstance();
        userName = currentUser.getCurrentUser().getUsername();
        String bookOwner = viewBook.getOwner().getUsername();
        System.out.println(userName);
        System.out.println(bookOwner);

        if (userName.equals(bookOwner)) { //user is looking at their own book (only happens when on myBooks page), can edit or delete, view requests, etc
            // hide request list at open of activity
            //requestAssets = new TextView[]{title, author, owner, status, ownerTitle,ISBN, isbnTitle, statusTitle };
            //reqDataList = new ArrayList<>();
            requestAssets = new TextView[]{title, author, owner, status, ISBN};
            reqView.setVisibility(View.INVISIBLE);
            requestsBtn.setEnabled(true);

            // determine status of book based on whether there is a borrower yet or not
            db.collection("Books").document(bookFSID).get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if (task.isSuccessful()) {
                                if (task.getResult().get("BorrowedBy") != null) {
                                    // borrower exists so book needs to be taken back
                                    receiveButton.setVisibility(View.VISIBLE);
                                } else if (task.getResult().get("AcceptedTo") != null) {
                                    // acceptor exists but no borrower yet so lend the book out
                                    lendButton.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    });


        } else { //user is looking at another user's book (from homepage), hide the edit, delete, two tabs buttons. Depending on the status of the book will show diff buttons (request, borrow, etc)
            editButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
            detailsBtn.setVisibility(View.GONE);
            requestsBtn.setVisibility(View.GONE);
            reqView.setVisibility(View.GONE);

            System.out.println("LOOKING AT OTHERS BOOK");
            // determine status of book based on whether there is a borrower yet or not
            db.collection("Books").document(bookFSID).get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if (task.isSuccessful()) {
                                if (task.getResult().get("BorrowedBy") != null) {
                                    if (task.getResult().get("AcceptedTo") != null) {
                                        // book has been accepted but not yet confirmed by borrower
                                        if (task.getResult().getString("AcceptedTo").equals(userName)) {
                                            // show button to borrower only
                                            borrowButton.setVisibility(View.VISIBLE);
                                        }
                                    } else {
                                        // book is borrowed and needs to be returned
                                        if (task.getResult().getString("BorrowedBy").equals(userName)) {
                                            // show button to borrower only
                                            returnButton.setVisibility(View.VISIBLE);
                                        }
                                    }
                                } else {
                                    //if book is available or has requests (and also make sure user hasn't requested it before) display request button
                                    //check is user has requested this book before
                                    if (viewBook.getStatus().equals("requested")) {
                                        //get all requesting users
                                        ArrayList<String> requestors = new ArrayList<String>();
                                        final boolean[] alreadyRequested = new boolean[1];
                                        CollectionReference requestsRef;
                                        //CollectionReference requestsRef = db.collection("Requests");
                                        requestsRef = db.collection("Requests");
                                        System.out.println("Got collection reference");
                                        Query query = requestsRef.whereEqualTo("bookID", bookFSID);
                                        query.get()
                                                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                    @Override
                                                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                        if (task.isSuccessful()) {
                                                            requestors.clear();
                                                            alreadyRequested[0] = false;
                                                            for (QueryDocumentSnapshot document : task.getResult()) {
                                                                System.out.println("In query document snapshot: " + document.getData().toString());
                                                                requestors.add(document.getData().toString());
                                                                String bookRequester = document.getString("requester");
                                                                //if requester is equal to user then show requested button and exit
                                                                System.out.println(alreadyRequested[0]);
                                                                if (bookRequester.equals(getUsername())) {
                                                                    alreadyRequested[0] = true;
                                                                    System.out.println(alreadyRequested[0]);
                                                                    //requestButton.setVisibility(View.INVISIBLE);
                                                                    requestedButton.setVisibility(View.VISIBLE);
                                                                }

                                                            }

                                                            if (!alreadyRequested[0]) {
                                                                requestButton.setVisibility(View.VISIBLE);
                                                            }
                                                        }

                                                    }
                                                });

                                    } else {
                                        requestButton.setVisibility(View.VISIBLE);
                                    }
                                }

                            }
                        }
                    });

        }

        /**
         * If Back Button is pressed, return to list of owned books, any changes in the book will be saved
         * and the book's information updated accordingly
         */
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // only return things from this intention if something was edited
                if ((title.getText().toString() != viewBook.getTitle()) ||
                        (author.getText().toString() != viewBook.getAuthor()) ||
                        (ISBN.equals(viewBook.getISBN()))){
                    viewBook.setTitle(title.getText().toString());
                    viewBook.setAuthor(author.getText().toString());
                    viewBook.setISBN(ISBN.getText().toString().replaceAll(" ", ""));

                    // return the book with its changed fields
                    Intent editedIntent = new Intent();
                    editedIntent.putExtra("edited book", viewBook);
                    setResult(2, editedIntent);
                }
                finish();
            }
        });

        /**
         * If Delete Button is pressed, return to list of owned books and pass this book along as marked
         * as to be deleted
         */
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //delete all requests for the book on firestore
                final FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("Requests").whereEqualTo("bookID", bookFSID)
                        .get()
                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    document.getReference().delete();
                                }
                            }
                        });

                // delete book from current user and firestore instance using the callback function
                currentUser(new Callback() {
                    @Override
                    public void onCallback(User user) {
                        // delete attached photograph, if it exists
                        StorageReference storageReference = FirebaseStorage.getInstance().getReference();
                        storageReference.child("books/" + bookFSID + ".jpg").delete()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        String TAG = "editBookFragment";
                                        Log.d(TAG, "onSuccess: deleted file");
                                    }
                                });

                        bookService.deleteBook(context, viewBook);  // delete book from firestore
                        Intent deleteIntent = new Intent();
                        deleteIntent.putExtra("delete book", viewBook); // mark book to be deleted in app
                        setResult(1, deleteIntent);
                        finish();
                    }
                });

            }
        });

        /**
         * If Edit Button is pressed, open EditBookFragment activity and pass it the book to edit its fields
         */
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                System.out.println("VIEWED BOOK FIRESTOREID: " + viewBook.getFirestoreID());
                Intent editIntent = new Intent(BookDetailsFragment.this, EditBookFragment.class);
                editIntent.putExtra("edit", viewBook);
                startActivityForResult(editIntent, 2);
            }
        });

        /**
         * If lend button is pressed, check if book brought to exchange is correct one to loan out
         */
        lendButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                // open scanner to check for correct book
                ScanButton(view);

            }
        });

        /**
         * If borrow button is pressed, check if book brought to exchange is correct one to borrow
         */
        borrowButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                // open scanner to check for correct book
                ScanButton(view);

            }
        });

        /**
         * If receive button is pressed, check if book brought to exchange is the one to be returned
         */
        receiveButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                // open scanner to check for correct book
                ScanButton(view);

            }
        });

        /**
         * If return button is pressed when a location has not yet been agreed upon, open map intent, check if the book brought to the exchange is the one that is to be returned.
         */
        returnButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onClick(View view) {
                // check if the user has been informed of where to exchange books
                db.collection("Books").document(viewBook.getFirestoreID()).get()
                        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    if (task.getResult().get("LatLang") == null) {
                                        // go to map intent
                                        Intent mapIntent = new Intent(context, requestMap.class);
                                        mapIntent.putExtra("bookID", viewBook.getFirestoreID());
                                        //get other user
                                        final FirebaseFirestore db = FirebaseFirestore.getInstance();
                                        DocumentReference docRef = db.collection("Books").document(viewBook.getFirestoreID());
                                        docRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                DocumentSnapshot document = task.getResult();
                                                String borrowedBy = document.getString("BorrowedBy");
                                                System.out.println("OTHER USER: " + borrowedBy);
                                                mapIntent.putExtra("otherUser", borrowedBy);
                                                startActivityForResult(mapIntent, 1);
                                            }
                                        });


                                    } else {
                                        // open scanner to check for correct book
                                        ScanButton(view);

                                    }
                                }
                            }
                        });
            }
        });

        // If Request Button is pressed, create new Request object, save to firestore, change Book status to request, and change the button
        requestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //change book status to requested

                viewBook.setStatus("requested");
                bookService.changeStatus(context, viewBook, "requested");
                requestButton.setVisibility(View.GONE);
                requestedButton.setVisibility(View.VISIBLE);
                requestedButton.setPressed(true);

                //create new request and store in firestore
                aRequest request = new aRequest(getUsername(), viewBook.getFirestoreID());
                System.out.println("Created new request: " + request);
                System.out.println("Request service: " + requestService);
                requestService.createRequest(request).addOnCompleteListener(task -> {
                    if (task.isSuccessful()){
                        Toast.makeText(getApplicationContext(), "Successfully requested book!", Toast.LENGTH_LONG).show();

                    }else{
                        System.out.println("Could not create request");
                        Toast.makeText(getApplicationContext(), "Unable to request book!", Toast.LENGTH_LONG).show();
                    }
                });

                //create notification
                addToNotifications(viewBook.getOwner().getUsername(), getUsername(), "Has requested to borrow your book: " + viewBook.getTitle(), "1", viewBook.getFirestoreID());

            }
        });

        // Toggles view, shows request list and hides book details
        requestsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (TextView asset : requestAssets) {
                    asset.setVisibility(View.GONE);
                }

                db = FirebaseFirestore.getInstance();
                System.out.println("viewBook.firstoreID: "+ viewBook.getFirestoreID());

                db.collection("Requests").whereEqualTo("bookID", viewBook.getFirestoreID())
                        .addSnapshotListener(new EventListener<QuerySnapshot>() {
                            @Override
                            public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                                System.out.println("INSIDE");
                                if (value != null) {
                                    requestList.clear();
                                    for (QueryDocumentSnapshot doc : value) {
                                        aRequest request = new aRequest(doc.getId(), doc.getString("requester"), doc.getString("bookID"));
                                        requestList.add(request);
                                    }
                                    System.out.println("Request list: "+requestList);
                                }
                                requestAdapter = new RequestAdapter(BookDetailsFragment.this, requestList);
                                reqView.setAdapter(requestAdapter);

                            }
                        });

                reqView = (RecyclerView) findViewById(R.id.reqList);
                reqView.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
                reqView.setVisibility(View.VISIBLE);
                
            }
        });

        // Toggles view, hides request list and shows book details
        detailsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (TextView asset : requestAssets) {
                    asset.setVisibility(View.VISIBLE);
                }
                reqView.setVisibility(View.GONE);

                db.collection("Books").document(viewBook.getFirestoreID()).get()
                        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                if (task.isSuccessful()) {
                                    if (task.getResult().get("BorrowedBy") != null) {
                                        // borrower exists so book needs to be taken back
                                        receiveButton.setVisibility(View.VISIBLE);
                                    } else if (task.getResult().get("AcceptedTo") != null) {
                                        // acceptor exists but no borrower yet so lend the book out
                                        lendButton.setVisibility(View.VISIBLE);
                                    }
                                }
                            }
                        });
            }
        });

        // Opens the profile of the user who owns the book.
        owner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, ProfileActivity.class);
                intent.putExtra("profile", owner.getText());
                startActivity(intent);
            }
        });
    }


    /**
     * Gets username of current user
     * @return String username
     */
    private String getUsername(){
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userName = user.getDisplayName();
        return userName;
    }


    /**
     *  When the Scan Button is pressed the scan activity is initiated
     * @param view the Scan Button
     */
    public void ScanButton(View view) {
        IntentIntegrator intentIntegrator = new IntentIntegrator(this);
        intentIntegrator.initiateScan();
    }


    /**
     *
     * @param imageId
     */
    private void convertImage(String imageId) {
        final long ONE_MEGABYTE = 1024 * 1024;
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        storageRef.child("books/" + imageId + ".jpg").getBytes(ONE_MEGABYTE)
                .addOnSuccessListener(bytes -> {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    editBitMap = bitmap;
                    photo.setImageBitmap(bitmap);
                }).addOnFailureListener(e -> {
            editBitMap = null;
            photo.setImageBitmap(null);
        });
    }

    /**
     * Logic for returning from EditBookFragment activity, if requestCode is 2 and resultCode is RESULT_OK
     * then edit the corresponding fields to match the passed book. Otherwise, logic for checking that the
     * information for the book scanned matches the information of the book being viewed.
     * @param requestCode 1 if returning from map intent, 2 if book is returned from the edit activity,
     *                    otherwise returning from scan activity
     * @param resultCode RESULT_OK if callee intents were successful
     * @param data Book object passed from the edit activity or scan intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            // let user know the book's owner received a return notification
            Toast.makeText(this, "Successfully sent return request to" + owner, Toast.LENGTH_SHORT).show();
        } else if (requestCode == 2) {
            if (resultCode == RESULT_OK) {
                // pass edited book back to parent activity
                Book editedBook = (Book) data.getSerializableExtra("edited");
                title.setText(editedBook.getTitle());
                author.setText(editedBook.getAuthor());
                owner.setText(editedBook.getOwner().getUsername());
                ISBN.setText(String.valueOf(editedBook.getISBN()));
                if(editedBook.getImageId() != null){
                    byte [] encodeByte= Base64.decode(editedBook.getImageId(),Base64.DEFAULT);
                    Bitmap bitmap=BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
                    editBitMap = bitmap;
                    photo.setImageBitmap(bitmap);
                } else {
                    editBitMap = null;
                    photo.setImageBitmap(null);
                }
            }
        } else {
            // check scanned book's information against the book being viewed
            IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

            if (intentResult != null) { //scanner got a result
                if (intentResult.getContents() == null) { //scanner worked, but was not able to get data
                    System.out.println("scanner worked, but not able to get data");
                    Toast toast = Toast.makeText(this, "Unable to obtain data from barcode",
                            Toast.LENGTH_SHORT); // used to display error message
                    toast.show();
                } else {
                    //got ISBN
                    //Use the ISBN to search through Google Books API to find the author, and title.
                    String isbn = intentResult.getContents();

                    // determine if the ISBN is correct
                    if (ISBN.getText().toString().equals(isbn)) {
                        checkISBN = true;
                    }

                    // if all information matches the book, change book status to returned
                    if (checkISBN) {
                        final aRequest request = new aRequest(userName, bookFSID);
                        if (userName.equals(owner.getText().toString())) {
                            // determine status of book based on whether there is a borrower yet or not
                            db.collection("Books").document(bookFSID).get()
                                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                            if (task.isSuccessful()) {
                                                if (task.getResult().get("BorrowedBy") != null) {
                                                    // take book back
                                                    handoverService.receiveBook(request)
                                                            .addOnCompleteListener((task1 -> {
                                                                viewBook.setStatus("available");
                                                                status.setText(viewBook.getStatus());
                                                                Toast.makeText(getApplicationContext(), "Successful book handover!", Toast.LENGTH_SHORT).show();

                                                                // return the book with its changed status
                                                                Intent editedIntent = new Intent();
                                                                editedIntent.putExtra("received book", viewBook);
                                                                finish();
                                                            }));
                                                } else if (task.getResult().get("AcceptedTo") != null) {
                                                    // lend book out
                                                    aRequest request2 = new aRequest(task.getResult().getString("AcceptedTo"), bookFSID);
                                                    handoverService.lendBook(request2)
                                                            .addOnCompleteListener((task1 -> {
                                                                viewBook.setStatus("borrowed");
                                                                status.setText(viewBook.getStatus());
                                                                Toast.makeText(getApplicationContext(), "Successful book handover!", Toast.LENGTH_SHORT).show();

                                                                // return the book with its changed status
                                                                Intent editedIntent = new Intent();
                                                                editedIntent.putExtra("lent book", viewBook);
                                                                finish();
                                                            }));
                                                }
                                            }
                                        }
                                    });
                        } else {
                            db.collection("Books").document(bookFSID).get()
                                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                            if (task.isSuccessful()) {
                                                if (task.getResult().get("BorrowedBy") != null) {
                                                    if (task.getResult().get("AcceptedTo") != null) {
                                                        // book has been accepted but not yet confirmed by borrower
                                                        if (task.getResult().getString("AcceptedTo").equals(userName)) {
                                                            // borrow book
                                                            handoverService.borrowBook(request)
                                                                    .addOnCompleteListener((task1 -> {
                                                                        viewBook.setStatus("borrowed");
                                                                        status.setText(viewBook.getStatus());
                                                                        Toast.makeText(getApplicationContext(), "Successful book handover!", Toast.LENGTH_SHORT).show();

                                                                        // return the book with its changed status
                                                                        Intent editedIntent = new Intent();
                                                                        editedIntent.putExtra("borrowed book", viewBook);
                                                                        finish();
                                                                    }));
                                                        }
                                                    } else {
                                                        // book is borrowed and needs to be returned
                                                        if (task.getResult().getString("BorrowedBy").equals(userName)) {
                                                            // return book
                                                            handoverService.returnBook(request)
                                                                    .addOnCompleteListener((task2 -> {
                                                                        if (task2.isSuccessful()) {
                                                                            viewBook.setStatus("available");
                                                                            status.setText(viewBook.getStatus());
                                                                            Toast.makeText(getApplicationContext(), "Successful book handover!", Toast.LENGTH_SHORT).show();

                                                                            // return the book with its changed status
                                                                            Intent editedIntent = new Intent();
                                                                            editedIntent.putExtra("returned book", viewBook);
                                                                            finish();
                                                                        }
                                                                    }));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "Failed to handover book! Book details do not match book to exchange", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
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

    /**
     * Adds a notification to the user collection in cloud firestore, so that it can be added to the
     * notification fragment for the specific user
     *
     * @param otherUser
     * @param user
     * @param notification
     * @param type
     * @param fireStoreID
     */
    private void addToNotifications(String otherUser, String user, String notification, String type, String fireStoreID){

        HashMap<Object, String> hashMap = new HashMap<>();
        hashMap.put("otherUser", otherUser);
        hashMap.put("user", user);
        hashMap.put("notification", notification);
        hashMap.put("type", type);
        hashMap.put("bookFSID", fireStoreID);


        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Users").document(otherUser).collection("Notifications").add(hashMap);

    }
}