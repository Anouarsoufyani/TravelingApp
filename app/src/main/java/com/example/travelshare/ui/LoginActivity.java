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
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private SessionManager sessionManager;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth          = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        if (sessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        setupLoginForm();
    }

    private void setupLoginForm() {
        etEmail    = findViewById(R.id.et_email);
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
            mAuth.signInAnonymously()
                    .addOnSuccessListener(r -> {
                        sessionManager.createAnonymousSession();
                        goToMain();
                    })
                    .addOnFailureListener(e -> {
                        // Si signInAnonymously échoue (pas de réseau), on continue quand même
                        sessionManager.createAnonymousSession();
                        goToMain();
                    });
        });
    }

    private void loginUser() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseRepository.getInstance().loadUserAccountByEmail(email, data -> {
                        if (data != null) {
                            String username = value(data, "username");
                            if (username == null || username.isEmpty()) username = fallbackUsername(email);
                            sessionManager.createLoginSession(stableIntId(username), username);
                            runOnUiThread(this::goToMain);
                            return;
                        }

                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            User user = AppDatabase.getInstance(this).userDao().getUserByEmail(email);
                            runOnUiThread(() -> {
                                if (user != null) {
                                    sessionManager.createLoginSession(user.id, user.login);
                                    FirebaseRepository.getInstance().saveUserAccount(
                                            user.login, user.email, user.nom, user.prenom,
                                            user.telephone, user.centresInteret);
                                    goToMain();
                                } else {
                                    FirebaseUser fbUser = authResult.getUser();
                                    String username = fbUser != null && fbUser.getDisplayName() != null
                                            && !fbUser.getDisplayName().trim().isEmpty()
                                            ? fbUser.getDisplayName().trim()
                                            : fallbackUsername(email);
                                    sessionManager.createLoginSession(stableIntId(username), username);
                                    FirebaseRepository.getInstance().saveUserAccount(
                                            username, email, "", "", "", "");
                                    Toast.makeText(this,
                                            "Compte récupéré depuis Firebase.",
                                            Toast.LENGTH_SHORT).show();
                                    goToMain();
                                }
                            });
                        });
                    });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Identifiants incorrects", Toast.LENGTH_SHORT).show());
    }

    private static String value(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    private static String fallbackUsername(String email) {
        if (email == null || email.trim().isEmpty()) return "user";
        int at = email.indexOf('@');
        String raw = at > 0 ? email.substring(0, at) : email;
        String clean = raw.replaceAll("[^a-zA-Z0-9_]", "_");
        return clean.isEmpty() ? "user" : clean;
    }

    private static int stableIntId(String username) {
        return Math.abs((username != null ? username.toLowerCase().hashCode() : 0));
    }

    private void goToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
