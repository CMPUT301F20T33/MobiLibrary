package com.example.mobilibrary;

import android.content.Intent;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mobilibrary.DatabaseController.User;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Objects;

import static android.app.Activity.RESULT_OK;

/**
 * @author Sooraj;
 * My Books fragment that is navigated to using notification bar. Contains a dropdown that organizes the User's books into status:
 * Owned, Requested, Accepted, and Borrowed. The user is able to see book title, author, isbn, and status.
 * The user is also able to add and edit their books opened from this Fragment
 */
public class MyBooksFragment extends Fragment {
    private static final String TAG = "MyBooksFragment";
    private ListView bookView;
    private ArrayAdapter<Book> bookAdapter;
    private ArrayList<Book> bookList;
    private FloatingActionButton addButton;

    private Spinner statesSpin;
    private String spinnerSelected = "owned";
    private static final String[] states = new String[]{"Owned", "Requested", "Accepted", "Borrowed"};
    private FirebaseFirestore db;

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

        /* we instantiate a new arraylist in case we have an empty firestore, if not we update this
        list later in updateBookList */

        bookList = new ArrayList<>();
        bookAdapter = new customBookAdapter(this.getActivity(), bookList);
        bookView.setAdapter(bookAdapter);
        updateBookList();

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
                spinnerSelected = statesSpin.getSelectedItem().toString().toLowerCase();;
                updateBookList();
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
     * @param requestCode type of code entering into the new activity
     * @param resultCode result code from finishing activity
     * @param data data from the finished activity
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
     * Used to fill bookList with firestore items, will get the information from the current User
     * Call back and use it to instantiate a new book object from the firestore information and add
     * it to the bookList (clears it in case we have new items and want to count them) and updates
     * adapter
     */
    public void updateBookList() {
        CurrentUser bookUser = CurrentUser.getInstance();
        System.out.println("IN UPDATE BOOKLIST");
        if (spinnerSelected.equals("owned")) {
            db.collection("Books").whereEqualTo("Owner", bookUser.getCurrentUser().getUsername()).orderBy("Title")
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
                                    if (doc.get("imageID") != null) {
                                        bookImage = Objects.requireNonNull(doc.get("imageID")).toString();
                                    }
                                    Book currentBook = new Book(bookId, bookTitle, bookISBN, bookAuthor, bookStatus, bookImage,
                                            bookUser.getCurrentUser());

                                    bookList.add(currentBook);
                                    bookAdapter.notifyDataSetChanged(); // Notifying the adapter to render any new data fetched from the cloud
                                }
                            }
                        }
                    });
        }
        else if (spinnerSelected.equals("requested")){
            db.collection("Books").addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                            if (value != null) {
                                bookList.clear();
                                for (final QueryDocumentSnapshot doc : value) {
                                    String bookId = doc.getId();
                                    String bookTitle = Objects.requireNonNull(doc.get("Title")).toString();
                                    String bookAuthor = Objects.requireNonNull(doc.get("Author")).toString();
                                    String bookISBN = Objects.requireNonNull(doc.get("ISBN")).toString();
                                    String bookStatus = Objects.requireNonNull(doc.get("Status")).toString();
                                    String bookOwner = Objects.requireNonNull(doc.get("Owner")).toString();
                                    if (doc.get("imageID") != null) {
                                        bookImage = Objects.requireNonNull(doc.get("imageID")).toString();
                                    }
                                    User otherUser = new User(bookOwner,"other", "other", "other");
                                    Book currentBook = new Book(bookId, bookTitle, bookISBN, bookAuthor, bookStatus, bookImage, otherUser);
                                    //Log.d("SOORAJ", currentBook.getFirestoreID());
                                    db.collection("Requests").whereEqualTo("requester", bookUser.getCurrentUser().getUsername())
                                            .addSnapshotListener(new EventListener<QuerySnapshot>() {
                                                @Override
                                                public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                                                    if (value != null) {
                                                        for (final QueryDocumentSnapshot doc : value) {
                                                            //Log.d("SOORAJ","REquest: " + Objects.requireNonNull(doc.get("bookID")).toString() );
                                                            if(currentBook.getFirestoreID().equals(Objects.requireNonNull(doc.get("bookID")).toString())){
                                                                bookList.add(currentBook);
                                                                bookAdapter.notifyDataSetChanged();
                                                                Log.d("SOORAJ", currentBook.getFirestoreID());
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                }
                            }
                        }
                    });

        }
    }
}