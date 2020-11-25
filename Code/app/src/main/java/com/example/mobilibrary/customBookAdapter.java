package com.example.mobilibrary;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;


import com.example.mobilibrary.DatabaseController.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Objects;

public class customBookAdapter extends RecyclerView.Adapter<customBookAdapter.MyViewHolder> implements Filterable {
    private ArrayList<Book> mBooks;
    private Context mContext;

    public customBookAdapter(Context context, ArrayList<Book> books){
        this.mContext = context;
        this.mBooks = books;
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.content, parent, false);

        MyViewHolder vh = new MyViewHolder(view);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        // get element from your dataset at this position
        // replace the contents of the view with that element
        holder.title.setText(mBooks.get(position).getTitle());
        System.out.println("Title " + mBooks.get(position).getTitle());
        holder.author.setText(mBooks.get(position).getAuthor());
        System.out.println("Author " + mBooks.get(position).getAuthor());
        holder.isbn.setText(mBooks.get(position).getISBN());
        System.out.println("ISBN " + mBooks.get(position).getISBN());


        //click listener
        holder.parentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //get image of book clicked
                byte[] bookImage = null;
                if (mBooks.get(position).getImageId() != null){
                    String bookImageString = mBooks.get(position).getImageId();
                    bookImage = Base64.decode(bookImageString, 0);
                }

                //Get the User object from currently clicked book by going into firestore
                final FirebaseFirestore db = FirebaseFirestore.getInstance();
                String owner_username = mBooks.get(position).getOwner().getUsername();
                DocumentReference doc = db.collection("Users").document(owner_username);
                if (bookImage != null){
                    Base64.encodeToString(bookImage, Base64.DEFAULT);
                }
                doc.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        DocumentSnapshot documentSnapshot = task.getResult();
                        System.out.println("Document snapshot data: " + documentSnapshot.getData());
                        System.out.println("Document snapshot data email: " + documentSnapshot.get("email"));
                        String username = Objects.requireNonNull(documentSnapshot.get("username").toString());
                        String email = Objects.requireNonNull(documentSnapshot.get("email").toString());
                        String name = Objects.requireNonNull(documentSnapshot.get("name").toString());
                        String phoneNo = Objects.requireNonNull(documentSnapshot.get("phoneNo").toString());

                        User user = new User(username, email, name, phoneNo);

                        //method to initiate book details intent
                        initIntent(user);
                    }
                    private void initIntent(User user) {
                        //get the book details of currently clicked item
                        Book newBook = new Book(mBooks.get(position).getFirestoreID(), mBooks.get(position).getTitle(), mBooks.get(position).getISBN(), mBooks.get(position).getAuthor(), mBooks.get(position).getStatus(), mBooks.get(position).getImageId(), user);
                        Intent viewBook = new Intent(mContext, BookDetailsFragment.class);
                        viewBook.putExtra("view book", newBook);
                        mContext.startActivity(viewBook);
                    }
                });
            }


        });

    }
    // Return the size of your dataset (invoked by the layout manager)

    @Override
    public int getItemCount() {
        return (mBooks == null) ? 0 : mBooks.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder{

        public TextView title;
        public TextView author;
        public TextView isbn;
        CardView parentLayout;
        public MyViewHolder(@NonNull View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.book_title2);
            author = (TextView) itemView.findViewById(R.id.book_author2);
            isbn = (TextView) itemView.findViewById(R.id.book_isbn2);
            parentLayout = (CardView) itemView.findViewById(R.id.parent_layout2);
        }


    }

    @Override
    public int getCount() {
        return filtered.size();
    }

    @Nullable
    @Override
    public Book getItem(int position) {
        return filtered.get(position);
    }

    /**
     * Filters adapter by a constraint entered by the user, used to search for specific books.
     * @return search result
     */
    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults result = new FilterResults();
                ArrayList<Book> found = new ArrayList<Book>();
                if (constraint.toString().length() > 0) {
                    if (isNumeric(constraint.toString())) {
                        for (Book b : books) {
                            if (b.getISBN().contains(constraint.toString())) {
                                found.add(b);
                            }
                        }
                    } else {
                        constraint = constraint.toString().toLowerCase();
                        for (Book b : books) {
                            Log.d(TAG, ("GET BOOK: " + b.getTitle() + " " + b.getAuthor() + " " + b.getOwner()));
                            if (b.getTitle().toLowerCase().contains(constraint) ||
                                    b.getAuthor().toLowerCase().contains(constraint)) {
                                found.add(b);
                            }
                        }
                    }
                    result.values = found;
                    result.count = found.size();
                } else {
                    result.values = books;
                    result.count = books.size();
                }
                return result;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filtered = (ArrayList<Book>) results.values;
                notifyDataSetChanged();
            }

        };
    }

    /**
     * Checks if a string is a number value. Used for ISBN search.
     * @param str string to be checked
     * @return bool
     */
    public static boolean isNumeric(String str) {
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
    }

}



