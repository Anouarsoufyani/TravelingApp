package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.travelshare.R;
import com.example.travelshare.data.models.TravelPlan;

public class PathPreferencesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_path_preferences, container, false);

        EditText etCity     = view.findViewById(R.id.et_path_city);
        EditText etBudget   = view.findViewById(R.id.et_path_budget);
        EditText etDuration = view.findViewById(R.id.et_path_duration);
        SeekBar  seekEffort = view.findViewById(R.id.seek_path_effort);
        CheckBox checkIndoor  = view.findViewById(R.id.check_indoor_only);
        CheckBox checkCulture = view.findViewById(R.id.check_activity_culture);
        CheckBox checkResto   = view.findViewById(R.id.check_activity_resto);
        CheckBox checkLoisirs = view.findViewById(R.id.check_activity_loisirs);
        Button   btnGenerate  = view.findViewById(R.id.btn_generate_path);

        btnGenerate.setOnClickListener(v -> {
            String city      = etCity.getText().toString().trim();
            String budgetStr = etBudget.getText().toString().trim();
            String durStr    = etDuration.getText().toString().trim();

            if (city.isEmpty()) {
                Toast.makeText(getContext(), "Veuillez indiquer une ville.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (budgetStr.isEmpty() || durStr.isEmpty()) {
                Toast.makeText(getContext(), "Veuillez indiquer un budget et une durée.", Toast.LENGTH_SHORT).show();
                return;
            }

            int budget   = Integer.parseInt(budgetStr);
            int duration = Integer.parseInt(durStr);

            // Convertir durée (minutes) → heures arrondi au supérieur
            int durationHours = Math.max(1, (int) Math.ceil(duration / 60.0));

            // Effort selon la position du curseur (0-4 → 5 niveaux)
            int effortLevel = seekEffort.getProgress();
            String effort = effortLevel <= 1 ? "Facile" : effortLevel <= 3 ? "Modéré" : "Intense";

            // Activités sélectionnées
            StringBuilder acts = new StringBuilder();
            if (checkCulture.isChecked()) { if (acts.length() > 0) acts.append(","); acts.append("Culture"); }
            if (checkResto.isChecked())   { if (acts.length() > 0) acts.append(","); acts.append("Restauration"); }
            if (checkLoisirs.isChecked()) { if (acts.length() > 0) acts.append(","); acts.append("Loisirs"); }
            if (acts.length() == 0) acts.append("Découverte");

            // Tolérance météo si intérieur coché
            String weather = checkIndoor.isChecked() ? "froid,chaleur,humidité" : "";

            // Construire un plan "fantôme" pour préremplir TravelPathFragment
            TravelPlan prefill = new TravelPlan();
            prefill.city              = city;
            prefill.budgetEur         = budget;
            prefill.durationHours     = durationHours;
            prefill.effort            = effort;
            prefill.activities        = acts.toString();
            prefill.requiredPlaces    = "";
            prefill.weatherTolerances = weather;

            TravelPathFragment tpFragment = TravelPathFragment.newInstanceForRegen(prefill);
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, tpFragment)
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }
}