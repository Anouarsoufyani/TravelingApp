package com.example.travelshare.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.travelshare.MainActivity;
import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.User;
import com.example.travelshare.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private SessionManager sessionManager;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth          = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        // Si Firebase a déjà une session active, aller direct à MainActivity
        if (mAuth.getCurrentUser() != null && sessionManager.getUserId() != -1) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        setupLoginForm();
    }

    private void setupLoginForm() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        Button btnLogin     = findViewById(R.id.btn_login);
        Button btnRegister  = findViewById(R.id.btn_register);
        Button btnAnonymous = findViewById(R.id.btn_anonymous);

        btnLogin.setOnClickListener(v -> loginUser());

        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("OPEN_FRAGMENT", "INSCRIPTION");
            startActivity(intent);
        });

        btnAnonymous.setOnClickListener(v -> {
            mAuth.signOut();
            sessionManager.logoutUser();
            goToMain();
        });
    }

    private void loginUser() {
        String login    = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (login.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = SessionManager.toFirebaseEmail(login);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Récupérer userId depuis Room
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        User user = AppDatabase.getInstance(this).userDao().getUserByLogin(login);
                        runOnUiThread(() -> {
                            if (user != null) {
                                sessionManager.createLoginSession(user.id, user.login);
                                goToMain();
                            } else {
                                Toast.makeText(this, "Utilisateur introuvable en base", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Identifiants incorrects", Toast.LENGTH_SHORT).show());
    }

    private void goToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
