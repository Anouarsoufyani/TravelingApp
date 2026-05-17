package com.example.travelshare.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.User;
import com.example.travelshare.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.Executors;

public class InscriptionFragment extends Fragment {

    public interface OnInscriptionListener {
        void onInscriptionReussie(long newUserId, String username);
    }

    private OnInscriptionListener listener;
    private FirebaseAuth mAuth;

    private EditText editLogin, editPassword, editNom, editPrenom,
            editDateNaissance, editTelephone, editEmail;
    private CheckBox checkVoyage, checkSport;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnInscriptionListener) {
            listener = (OnInscriptionListener) context;
        } else {
            throw new RuntimeException(context + " must implement OnInscriptionListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inscription, container, false);

        mAuth = FirebaseAuth.getInstance();

        editLogin         = view.findViewById(R.id.edit_login);
        editPassword      = view.findViewById(R.id.edit_password);
        editNom           = view.findViewById(R.id.edit_nom);
        editPrenom        = view.findViewById(R.id.edit_prenom);
        editDateNaissance = view.findViewById(R.id.edit_date_naissance);
        editTelephone     = view.findViewById(R.id.edit_telephone);
        editEmail         = view.findViewById(R.id.edit_email);
        checkVoyage       = view.findViewById(R.id.check_voyage);
        checkSport        = view.findViewById(R.id.check_sport);

        view.findViewById(R.id.btn_valider_inscription)
                .setOnClickListener(v -> validerInscription());

        return view;
    }

    private void validerInscription() {
        String login         = editLogin.getText().toString().trim();
        String password      = editPassword.getText().toString().trim();
        String nom           = editNom.getText().toString().trim();
        String prenom        = editPrenom.getText().toString().trim();
        String dateNaissance = editDateNaissance.getText().toString().trim();
        String telephone     = editTelephone.getText().toString().trim();
        String email         = editEmail.getText().toString().trim();

        StringBuilder interets = new StringBuilder();
        if (checkVoyage.isChecked()) interets.append("Voyage ");
        if (checkSport.isChecked())  interets.append("Sport");

        if (!login.matches("^[a-zA-Z].*") || login.length() > 10) {
            editLogin.setError("Doit commencer par une lettre, max 10 car.");
            return;
        }
        if (password.length() < 6) {
            editPassword.setError("Doit contenir au moins 6 caractères");
            return;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Email invalide");
            return;
        }

        AppDatabase db = AppDatabase.getInstance(getContext());

        Executors.newSingleThreadExecutor().execute(() -> {
            User existant = db.userDao().getUserByLogin(login);
            if (existant != null) {
                if (getActivity() != null)
                    getActivity().runOnUiThread(() ->
                            editLogin.setError("Ce login existe déjà !"));
                return;
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                    mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {

                            User newUser = new User();
                            newUser.login         = login;
                            newUser.nom           = nom;
                            newUser.prenom        = prenom;
                            newUser.dateNaissance = dateNaissance;
                            newUser.telephone     = telephone;
                            newUser.email         = email;
                            newUser.centresInteret = interets.toString();

                            Executors.newSingleThreadExecutor().execute(() -> {
                                long newUserId = db.userDao().insertUser(newUser);
                                if (getActivity() != null)
                                    getActivity().runOnUiThread(() ->
                                            listener.onInscriptionReussie(newUserId, login));
                            });
                        })
                        .addOnFailureListener(e -> {
                            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                                editLogin.setError("Ce login existe déjà !");
                            } else {
                                editLogin.setError("Erreur : " + e.getMessage());
                            }
                        })
                );
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
}
