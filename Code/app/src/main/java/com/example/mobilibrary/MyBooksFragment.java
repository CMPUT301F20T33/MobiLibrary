package com.example.mobilibrary;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;


import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mobilibrary.DatabaseController.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StreamDownloadTask;

import java.util.ArrayList;
import java.util.Objects;

import static android.app.Activity.RESULT_OK;

/**
 * My Books fragment that is navigated to using notification bar. Contains a dropdown that organizes the User's books into status:
 * Owned, Requested, Accepted, and Borrowed. The user is able to see book title, author, isbn, and status.
 * The user is also able to add and edit their books in this Fragment
 *
 */
public class MyBooksFragment extends Fragment {
    private static final String TAG = "MyBooksFragment";
    private ListView bookView;
    private ArrayAdapter<Book> bookAdapter;
    private ArrayList<Book> bookList;
    private FloatingActionButton addButton;

    private Spinner statesSpin;
    private static final String[] states = new String[]{"Owned", "Requested", "Accepted", "Borrowed"};
    private FirebaseFirestore db;
    private FirebaseUser userInfo;

    private String bookImage;

    public MyBooksFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        System.out.println("In MyBooks Fragment");
        // Inflate the layout for this fragment
        View v =  inflater.inflate(R.layout.fragment_my_books, container, false);
        addButton = v.findViewById(R.id.addButton);
        bookView = v.findViewById(R.id.book_list);
        db = FirebaseFirestore.getInstance();
        userInfo = FirebaseAuth.getInstance().getCurrentUser();

        /* we instantiate a new arraylist in case we have an empty firestore, if not we update this
        list later in updateBookList */

        bookList = new ArrayList<>();
        bookAdapter = new customBookAdapter(this.getActivity(), bookList);
        bookView.setAdapter(bookAdapter);
        currentUser(new Callback() {
            @Override
            public void onCallback(User user) {
                updateBookList(user);
            }
        });

        statesSpin = (Spinner) v.findViewById(R.id.spinner);
        ArrayAdapter<String> SpinAdapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, states);
        SpinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statesSpin.setAdapter(SpinAdapter);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent addIntent = new Intent(getActivity(), AddBookFragment.class);
                startActivity(addIntent);
            }
        });

        bookView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Book book = bookList.get(i);
                Intent viewBook = new Intent(getActivity(), BookDetailsFragment.class);
                viewBook.putExtra("view book", book);
                // viewBook.putExtra("book owner", user.getusername());   // need to get user somehow, add User variable to this class
                startActivityForResult(viewBook, 1);
            }
        });

        statesSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String state = (String) adapterView.getItemAtPosition(i);
                currentUser(new Callback() {
                    @Override
                    public void onCallback(final User user){
                                updateBookList(user);
                    }
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        return v;
    }

    /**
     * If requestCode is 0, if its 1, we are either deleting a book (result code =1) or editing
     * an existing book (result code = 2) with data.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("IN ONACTIVITYRESULT");
        /*if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                Book new_book = (Book) Objects.requireNonNull(data.getExtras()).getSerializable("new book");
                bookAdapter.add(new_book);
                bookAdapter.notifyDataSetChanged();
                System.out.println("Book adaptor added newBook");
            }
        }*/

        if (requestCode == 1) {
            if (resultCode == 1) {
                // book needs to be deleted, intent has book to delete
                Book delete_book = (Book) data.getSerializableExtra("delete book");

                // find the book to delete and delete it
                for (int i = 0; i < bookAdapter.getCount(); i++) {
                    Book currentBook = bookAdapter.getItem(i);
                    if (delete_book.getFirestoreID().equals(currentBook.getFirestoreID())) {
                        bookAdapter.remove(currentBook);
                    }
                }

                bookAdapter.notifyDataSetChanged();
            } else if (resultCode == 2) {
                // book was edited update data set
                Book edited_book = (Book) data.getSerializableExtra("edited book");

                // find the book to edit and edit it
                for (int i = 0; i < bookList.size(); i++) {
                    Book currentBook = bookList.get(i);
                    if (edited_book.getFirestoreID().equals(currentBook.getFirestoreID())) {
                        currentBook.setTitle(edited_book.getTitle());
                        currentBook.setAuthor(edited_book.getAuthor());
                        currentBook.setISBN(edited_book.getISBN());
                        currentBook.setImageId(edited_book.getImageId());
                    }
                }
                bookAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Used to get the current user from the user collection of firestore and returns it on
     * a Callback because OnCompleteListener is asynchronous
     *
     * @param cbh
     */

    public void currentUser(final Callback cbh) {
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
     * Used to fill bookList with firestore items, will get the information from the current User
     * Call back and use it to instantiate a new book object from the firestore information and add
     * it to the bookList (clears it in case we have new items and want to count them) and updates
     * adapter
     *
     * @param bookUser
     */
    public void updateBookList(final User bookUser) {
        System.out.println("IN UPDATE BOOKLIST");
        db.collection("Books").whereEqualTo("Owner", bookUser.getUsername()).orderBy("Title")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        if (value != null) {
                            bookList.clear();
                            for (final QueryDocumentSnapshot doc : value) {
                                Log.d(TAG, String.valueOf(doc.getData().get("Owner")));
                                String bookId = doc.getId();
                                String bookTitle = Objects.requireNonNull(doc.get("Title")).toString();
                                String bookAuthor = Objects.requireNonNull(doc.get("Author")).toString();
                                String bookISBN = Objects.requireNonNull(doc.get("ISBN")).toString();
                                String bookStatus = Objects.requireNonNull(doc.get("Status")).toString();
                                if(doc.get("imageID") != null) {
                                   bookImage = Objects.requireNonNull(doc.get("imageID")).toString();
                                }
                                Book currentBook = new Book(bookId, bookTitle, bookISBN, bookAuthor, bookStatus, bookImage, bookUser);
                                String currState = statesSpin.getSelectedItem().toString().toLowerCase();
                                if (!currState.equals("owned")) {
                                    if (currState.equals(bookStatus)) {
                                        bookList.add(currentBook);
                                    }
                                } else {
                                    bookList.add(currentBook);
                                }
                                bookAdapter.notifyDataSetChanged(); // Notifying the adapter to render any new data fetched from the cloud
                            }
                        }
                    }
                });
    }

    /*public void addImage(final Book book) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference();
        StorageReference dateRef = storageRef.child(book.getFirestoreID());
        dateRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>()
        {
            @Override
            public void onSuccess(Uri downloadUrl)
            {
                book.setImage(downloadUrl);
            }
        });
    }*/

    /*public void setBitMap(final Book book) {
        //Bitmap bitMap = null;
        System.out.println("IN SETBITMAP");
        StorageReference storageRef = FirebaseStorage.getInstance().getReference("books/" + book.getFirestoreID()+ ".jpg");
        StreamDownloadTask stream = storageRef.getStream();
        System.out.println("After storageref");


        //THIS IS WHERE PROBLEM IS ********
        final long ONE_MEGABYTE = 1024 * 1024;
        storageRef.getBytes(ONE_MEGABYTE)
                .addOnSuccessListener(new OnSuccessListener<byte[]>() {
                    @Override
                    public void onSuccess(byte[] bytes) {
                        Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        book.setImage(bm);
                        System.out.println("BOOKIMAGE SET");
                    }

                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle any errors
            }
        }); */




        /*dateRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri downloadUrl)
            {
                try {
                    System.out.println("IN TRY");
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), downloadUrl);
                    book.setImage(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });*/
}