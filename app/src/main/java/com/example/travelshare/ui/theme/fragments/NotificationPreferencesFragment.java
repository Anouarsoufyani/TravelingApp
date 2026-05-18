package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.util.ArrayList;
import java.util.List;

public class NotificationPreferencesFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification_preferences, container, false);

        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        SessionManager session = new SessionManager(requireContext());
        long userId = session.getUserId();

        view.findViewById(R.id.btn_pref_back).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        Spinner spinnerType = view.findViewById(R.id.spinner_pref_type);
        String[] typeLabels = {"AUTEUR", "LIEU", "ÉTIQUETTE", "GROUPE"};
        String[] typeValues = {"AUTEUR", "LIEU", "TAG", "GROUPE"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_spinner_item, typeLabels) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) super.getView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTextSize(14f);
                return tv;
            }
            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setBackgroundColor(android.graphics.Color.parseColor("#1A2040"));
                tv.setPadding(32, 24, 32, 24);
                return tv;
            }
        };
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(typeAdapter);

        EditText etValueHint = view.findViewById(R.id.et_pref_value);
        spinnerType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                switch (typeValues[pos]) {
                    case "AUTEUR":  etValueHint.setHint("Nom d'auteur (ex: marie)"); break;
                    case "LIEU":    etValueHint.setHint("Lieu (ex: Paris)"); break;
                    case "TAG":     etValueHint.setHint("Étiquette (ex: plage)"); break;
                    case "GROUPE":  etValueHint.setHint("Nom du groupe"); break;
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        RecyclerView rv = view.findViewById(R.id.rv_prefs);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        PrefAdapter adapter = new PrefAdapter(viewModel, session.getUsername());
        rv.setAdapter(adapter);

        viewModel.getPreferencesForUser(userId).observe(getViewLifecycleOwner(), adapter::setPrefs);

        if (session.isLoggedIn()) {
            FirebaseRepository.getInstance().syncNotificationPreferences(
                    session.getUsername(), AppDatabase.getInstance(requireContext()), userId, null);
        }

        EditText etValue = view.findViewById(R.id.et_pref_value);
        view.findViewById(R.id.btn_add_pref).setOnClickListener(v -> {
            String value = etValue.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(getContext(), "Entrez une valeur", Toast.LENGTH_SHORT).show();
                return;
            }
            int selectedIndex = spinnerType.getSelectedItemPosition();
            String type = typeValues[Math.max(0, selectedIndex)];
            NotificationPreference pref = new NotificationPreference();
            pref.userId = userId;
            pref.type   = type;
            pref.value  = value;
            viewModel.insertPreference(pref);
            FirebaseRepository.getInstance().saveNotificationPreference(session.getUsername(), type, value);
            etValue.setText("");
            Toast.makeText(getContext(), "Alerte ajoutée !", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    static class PrefAdapter extends RecyclerView.Adapter<PrefAdapter.PVH> {
        private List<NotificationPreference> prefs = new ArrayList<>();
        private final SharedViewModel viewModel;
        private final String username;

        PrefAdapter(SharedViewModel vm, String username) {
            this.viewModel = vm;
            this.username  = username;
        }

        static class PVH extends RecyclerView.ViewHolder {
            TextView tvType, tvValue, btnDelete;
            PVH(View v) {
                super(v);
                tvType    = v.findViewById(R.id.tv_pref_type);
                tvValue   = v.findViewById(R.id.tv_pref_value);
                btnDelete = v.findViewById(R.id.btn_delete_pref);
            }
        }

        @NonNull @Override
        public PVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notif_pref, parent, false);
            return new PVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PVH h, int position) {
            NotificationPreference p = prefs.get(position);
            h.tvType.setText(displayType(p.type));
            h.tvValue.setText(p.value);
            h.btnDelete.setOnClickListener(v -> {
                viewModel.deletePreference(p);
                FirebaseRepository.getInstance().deleteNotificationPreference(username, p.type, p.value);
            });
        }

        @Override public int getItemCount() { return prefs.size(); }

        private String displayType(String type) {
            return "TAG".equals(type) ? "ÉTIQUETTE" : type;
        }

        void setPrefs(List<NotificationPreference> list) {
            this.prefs = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
