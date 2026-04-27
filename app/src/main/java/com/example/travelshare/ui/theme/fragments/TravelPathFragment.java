package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.models.TravelPlan;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.TravelPathViewModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TravelPathFragment extends Fragment {

    private static final String ARG_CITY = "prefill_city";

    public static TravelPathFragment newInstance(String city) {
        TravelPathFragment f = new TravelPathFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CITY, city);
        f.setArguments(args);
        return f;
    }

    private TravelPathViewModel viewModel;
    private SessionManager session;
    private PlanAdapter adapter;
    private boolean showSaved = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_travel_path, container, false);

        viewModel = new ViewModelProvider(this).get(TravelPathViewModel.class);
        session   = new SessionManager(requireContext());
        long userId = session.getUserId();

        // Pré-remplir la ville si transmise via arguments (passerelle TravelShare)
        String prefillCity = getArguments() != null ? getArguments().getString(ARG_CITY, "") : "";
        if (!prefillCity.isEmpty()) {
            ((android.widget.EditText) view.findViewById(R.id.et_tp_city)).setText(prefillCity);
            // Bannière passerelle
            android.widget.TextView tvBanner = new android.widget.TextView(getContext());
            tvBanner.setText("📍 Destination importée depuis TravelShare");
            tvBanner.setTextColor(requireContext().getResources().getColor(R.color.teal, null));
            tvBanner.setTextSize(12f);
            tvBanner.setPadding(16, 8, 16, 0);
            ((android.view.ViewGroup) view.findViewById(R.id.layout_tp_form)).addView(tvBanner, 0);
        }

        RecyclerView rv = view.findViewById(R.id.rv_tp_plans);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PlanAdapter(viewModel, this);
        rv.setAdapter(adapter);

        TextView tvResultsLabel = view.findViewById(R.id.tv_tp_results_label);
        TextView tvSavedTab = view.findViewById(R.id.tv_tp_saved_tab);

        // Observer unique — on switche la source selon l'onglet
        androidx.lifecycle.MediatorLiveData<List<TravelPlan>> merged = new androidx.lifecycle.MediatorLiveData<>();
        androidx.lifecycle.LiveData<List<TravelPlan>> allPlans   = viewModel.getPlansForUser(userId);
        androidx.lifecycle.LiveData<List<TravelPlan>> savedPlans = viewModel.getSavedPlansForUser(userId);
        merged.addSource(allPlans,   list -> { if (!showSaved) merged.setValue(list); });
        merged.addSource(savedPlans, list -> { if (showSaved)  merged.setValue(list); });
        merged.observe(getViewLifecycleOwner(), adapter::setPlans);

        // ── Onglet Sauvegardés ──────────────────────────────────────────────
        tvSavedTab.setOnClickListener(v -> {
            showSaved = !showSaved;
            if (showSaved) {
                tvSavedTab.setText("Tous les parcours");
                tvResultsLabel.setText("PARCOURS SAUVEGARDÉS");
                tvResultsLabel.setVisibility(View.VISIBLE);
                merged.setValue(savedPlans.getValue());
            } else {
                tvSavedTab.setText("Sauvegardés");
                tvResultsLabel.setVisibility(View.GONE);
                merged.setValue(allPlans.getValue());
            }
        });

        // ── SeekBars ────────────────────────────────────────────────────────
        SeekBar sbBudget   = view.findViewById(R.id.seekbar_tp_budget);
        SeekBar sbDuration = view.findViewById(R.id.seekbar_tp_duration);
        TextView tvBudget  = view.findViewById(R.id.tv_tp_budget_value);
        TextView tvDur     = view.findViewById(R.id.tv_tp_duration_value);

        sbBudget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                tvBudget.setText((p < 10 ? 10 : p) + " €");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        sbDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                int h = p + 1;
                tvDur.setText(h + " h");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // ── Génération ──────────────────────────────────────────────────────
        view.findViewById(R.id.btn_tp_generate).setOnClickListener(v -> {
            String city = ((android.widget.EditText) view.findViewById(R.id.et_tp_city))
                    .getText().toString().trim();
            if (city.isEmpty()) {
                Toast.makeText(getContext(), "Entrez une destination", Toast.LENGTH_SHORT).show();
                return;
            }

            Set<String> activities = new LinkedHashSet<>();
            if (((CheckBox) view.findViewById(R.id.cb_culture)).isChecked())      activities.add("Culture");
            if (((CheckBox) view.findViewById(R.id.cb_restauration)).isChecked()) activities.add("Restauration");
            if (((CheckBox) view.findViewById(R.id.cb_loisirs)).isChecked())      activities.add("Loisirs");
            if (((CheckBox) view.findViewById(R.id.cb_decouverte)).isChecked())   activities.add("Découverte");
            if (((CheckBox) view.findViewById(R.id.cb_shopping)).isChecked())     activities.add("Shopping");

            if (activities.isEmpty()) {
                Toast.makeText(getContext(), "Sélectionnez au moins une activité", Toast.LENGTH_SHORT).show();
                return;
            }

            int budget   = Math.max(10, sbBudget.getProgress());
            int duration = sbDuration.getProgress() + 1;

            String effort = "Facile";
            int rbId = ((RadioGroup) view.findViewById(R.id.rg_tp_effort)).getCheckedRadioButtonId();
            if (rbId == R.id.rb_modere) effort = "Modéré";
            else if (rbId == R.id.rb_intense) effort = "Intense";

            String requiredPlaces = ((android.widget.EditText) view.findViewById(R.id.et_tp_required_places))
                    .getText().toString().trim();

            Set<String> weatherTolerances = new LinkedHashSet<>();
            if (((CheckBox) view.findViewById(R.id.cb_cold)).isChecked())     weatherTolerances.add("FROID");
            if (((CheckBox) view.findViewById(R.id.cb_heat)).isChecked())     weatherTolerances.add("CHALEUR");
            if (((CheckBox) view.findViewById(R.id.cb_humidity)).isChecked()) weatherTolerances.add("HUMIDITE");

            view.findViewById(R.id.btn_tp_generate).setEnabled(false);
            Toast.makeText(getContext(), "Génération en cours…", Toast.LENGTH_SHORT).show();

            final String finalEffort = effort;
            viewModel.generatePlans(userId, city, activities, budget, duration, finalEffort,
                    requiredPlaces, weatherTolerances, () ->
                requireActivity().runOnUiThread(() -> {
                    view.findViewById(R.id.btn_tp_generate).setEnabled(true);
                    tvResultsLabel.setVisibility(View.VISIBLE);
                    tvResultsLabel.setText("PARCOURS GÉNÉRÉS");
                    viewModel.getPlansForUser(userId).observe(getViewLifecycleOwner(), adapter::setPlans);
                    Toast.makeText(getContext(), "Parcours prêts !", Toast.LENGTH_SHORT).show();
                })
            );
        });

        return view;
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    static class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PVH> {
        private List<TravelPlan> plans = new ArrayList<>();
        private final TravelPathViewModel viewModel;
        private final Fragment fragment;

        PlanAdapter(TravelPathViewModel vm, Fragment f) {
            this.viewModel = vm;
            this.fragment  = f;
        }

        static class PVH extends RecyclerView.ViewHolder {
            View vColor;
            TextView tvBadge, tvCity, tvLiked, tvBudget, tvDuration, tvEffort, tvActivities;
            android.widget.Button btnDetail;

            PVH(View v) {
                super(v);
                vColor      = v.findViewById(R.id.v_plan_color);
                tvBadge     = v.findViewById(R.id.tv_plan_type_badge);
                tvCity      = v.findViewById(R.id.tv_plan_city);
                tvLiked     = v.findViewById(R.id.tv_plan_liked);
                tvBudget    = v.findViewById(R.id.tv_plan_budget);
                tvDuration  = v.findViewById(R.id.tv_plan_duration);
                tvEffort    = v.findViewById(R.id.tv_plan_effort);
                tvActivities = v.findViewById(R.id.tv_plan_activities);
                btnDetail   = v.findViewById(R.id.btn_plan_detail);
            }
        }

        @NonNull @Override
        public PVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PVH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_plan_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PVH h, int position) {
            TravelPlan p = plans.get(position);

            // Couleur et label selon type
            int color;
            String label;
            switch (p.type) {
                case "economique":
                    color = h.itemView.getContext().getResources().getColor(R.color.teal, null);
                    label = "Économique";
                    break;
                case "confort":
                    color = h.itemView.getContext().getResources().getColor(R.color.terracotta, null);
                    label = "Confort";
                    break;
                default:
                    color = h.itemView.getContext().getResources().getColor(R.color.navy, null);
                    label = "Équilibré";
            }
            h.vColor.setBackgroundColor(color);
            h.tvBadge.setBackgroundColor(color);
            h.tvBadge.setText(label);
            h.tvCity.setText(p.city);
            h.tvLiked.setText(p.liked ? "♥" : "♡");
            h.tvBudget.setText(p.budgetEur + " €");
            h.tvDuration.setText(p.durationHours + " h");
            h.tvEffort.setText(p.effort);
            h.tvActivities.setText(p.activities != null ? p.activities.replace(",", " · ") : "");

            h.tvLiked.setOnClickListener(v -> {
                p.liked = !p.liked;
                p.saved = p.liked;
                viewModel.saveLikeAndSave(p);
                h.tvLiked.setText(p.liked ? "♥" : "♡");
            });

            h.btnDetail.setOnClickListener(v ->
                fragment.requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, PlanDetailFragment.newInstance(p.id))
                        .addToBackStack(null)
                        .commit()
            );
        }

        @Override public int getItemCount() { return plans.size(); }

        void setPlans(List<TravelPlan> list) {
            this.plans = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
