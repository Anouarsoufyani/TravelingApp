package com.example.travelshare.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.travelshare.MainActivity;
import com.example.travelshare.R;

public class AccueilActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accueil);

        Button btnInscription = findViewById(R.id.btn_accueil_inscription);
        Button btnConnexion = findViewById(R.id.btn_accueil_connexion);

        btnInscription.setOnClickListener(v -> {
            Intent intent = new Intent(AccueilActivity.this, MainActivity.class);
            intent.putExtra("OPEN_FRAGMENT", "INSCRIPTION");
            startActivity(intent);
        });

        btnConnexion.setOnClickListener(v -> {

            Intent intent = new Intent(AccueilActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }
}
