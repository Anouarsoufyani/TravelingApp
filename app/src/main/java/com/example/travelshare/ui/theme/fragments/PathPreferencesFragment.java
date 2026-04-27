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

public class PathPreferencesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_path_preferences, container, false);


        EditText etBudget = view.findViewById(R.id.et_path_budget);
        EditText etDuration = view.findViewById(R.id.et_path_duration);


        SeekBar seekEffort = view.findViewById(R.id.seek_path_effort);


        CheckBox checkIndoor = view.findViewById(R.id.check_indoor_only); // "Je veux rester à l'abri"
        CheckBox checkCulture = view.findViewById(R.id.check_activity_culture);
        CheckBox checkResto = view.findViewById(R.id.check_activity_resto);
        CheckBox checkLoisirs = view.findViewById(R.id.check_activity_loisirs);

        Button btnGenerate = view.findViewById(R.id.btn_generate_path);

        btnGenerate.setOnClickListener(v -> {
            String budgetStr = etBudget.getText().toString();
            String durationStr = etDuration.getText().toString();

            if (budgetStr.isEmpty() || durationStr.isEmpty()) {
                Toast.makeText(getContext(), "Veuillez indiquer un budget et une durée.", Toast.LENGTH_SHORT).show();
                return;
            }

            int budget = Integer.parseInt(budgetStr);
            int duration = Integer.parseInt(durationStr);
            int effortLevel = seekEffort.getProgress();
            boolean needsIndoor = checkIndoor.isChecked();

            Toast.makeText(getContext(), "Calcul des options de parcours en cours...", Toast.LENGTH_SHORT).show();


        });

        return view;
    }
}