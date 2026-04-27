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
import java.util.concurrent.Executors;

public class InscriptionFragment extends Fragment {

    public interface OnInscriptionListener {
        void onInscriptionReussie(long newUserId, String username);
    }

    private OnInscriptionListener listener;

    private EditText editLogin, editPassword, editNom, editPrenom, editDateNaissance, editTelephone, editEmail;
    private CheckBox checkVoyage, checkSport;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnInscriptionListener) {
            listener = (OnInscriptionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnInscriptionListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inscription, container, false);

        editLogin = view.findViewById(R.id.edit_login);
        editPassword = view.findViewById(R.id.edit_password);
        editNom = view.findViewById(R.id.edit_nom);
        editPrenom = view.findViewById(R.id.edit_prenom);
        editDateNaissance = view.findViewById(R.id.edit_date_naissance);
        editTelephone = view.findViewById(R.id.edit_telephone);
        editEmail = view.findViewById(R.id.edit_email);
        checkVoyage = view.findViewById(R.id.check_voyage);
        checkSport = view.findViewById(R.id.check_sport);

        Button btnValider = view.findViewById(R.id.btn_valider_inscription);
        btnValider.setOnClickListener(v -> validerInscription());

        return view;
    }

    private void validerInscription() {
        String login = editLogin.getText().toString().trim();
        String password = editPassword.getText().toString().trim();
        String nom = editNom.getText().toString().trim();
        String prenom = editPrenom.getText().toString().trim();
        String dateNaissance = editDateNaissance.getText().toString().trim();
        String telephone = editTelephone.getText().toString().trim();
        String email = editEmail.getText().toString().trim();

        // Centres d'intérêt compilés
        StringBuilder interets = new StringBuilder();
        if (checkVoyage.isChecked()) interets.append("Voyage ");
        if (checkSport.isChecked()) interets.append("Sport");

        // Login : commence par une lettre , 10 caracteres max
        if (!login.matches("^[a-zA-Z].*") || login.length() > 10) {
            editLogin.setError("Doit commencer par une lettre, max 10 car.");
            return;
        }
        
        // Mot de passe : au moins 6 caracteres
        if (password.length() < 6) {
            editPassword.setError("Doit contenir au moins 6 caracteres");
            return;
        }

        User newUser = new User();
        newUser.login = login;
        newUser.password = password;
        newUser.nom = nom;
        newUser.prenom = prenom;
        newUser.dateNaissance = dateNaissance;
        newUser.telephone = telephone;
        newUser.email = email;
        newUser.centresInteret = interets.toString();

        AppDatabase db = AppDatabase.getInstance(getContext());

        Executors.newSingleThreadExecutor().execute(() -> {
            User existant = db.userDao().getUserByLogin(login);
            if (existant != null) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> editLogin.setError("Ce login existe deja !"));
                }
            } else {
                long newUserId = db.userDao().insertUser(newUser);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> listener.onInscriptionReussie(newUserId, login));
                }
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
}
