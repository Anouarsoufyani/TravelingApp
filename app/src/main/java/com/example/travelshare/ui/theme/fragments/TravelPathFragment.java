package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TravelPathFragment extends Fragment {

    private static final String ARG_CITY = "prefill_city";
    private static final String ARG_REQ  = "prefill_req";

    public static TravelPathFragment newInstance(String city) {
        return newInstance(city, "");
    }

    public static TravelPathFragment newInstance(String city, String req) {
        TravelPathFragment f = new TravelPathFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CITY, city);
        args.putString(ARG_REQ,  req);
        f.setArguments(args);
        return f;
    }

    private TravelPathViewModel viewModel;
    private SessionManager session;
    private View sectionResults;
    private View scrollForm;
    private RecyclerView rvResults, rvSaved;
    private PlanAdapter resultsAdapter, savedAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_travel_path, container, false);

        viewModel = new ViewModelProvider(this).get(TravelPathViewModel.class);
        session   = new SessionManager(requireContext());
        long userId = session.getUserId();

        scrollForm     = view.findViewById(R.id.scroll_tp);
        sectionResults = view.findViewById(R.id.section_tp_results);
        rvResults      = view.findViewById(R.id.rv_tp_results);
        rvSaved        = view.findViewById(R.id.rv_tp_saved);

        setupRecyclerViews();
        setupForm(view, userId);
        setupTabs(view);

        // Observe résultats — affiche la section si non vide
        viewModel.getPlansForUser(userId).observe(getViewLifecycleOwner(), plans -> {
            resultsAdapter.setPlans(plans);
            sectionResults.setVisibility(
                    plans != null && !plans.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getSavedPlansForUser(userId).observe(getViewLifecycleOwner(), savedAdapter::setPlans);

        return view;
    }

    private void setupTabs(View view) {
        TabLayout tabs = view.findViewById(R.id.tabs_tp);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    scrollForm.setVisibility(View.VISIBLE);
                    rvSaved.setVisibility(View.GONE);
                } else {
                    scrollForm.setVisibility(View.GONE);
                    rvSaved.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerViews() {
        rvResults.setLayoutManager(new LinearLayoutManager(getContext()));
        resultsAdapter = new PlanAdapter(viewModel, this);
        rvResults.setAdapter(resultsAdapter);

        rvSaved.setLayoutManager(new LinearLayoutManager(getContext()));
        savedAdapter = new PlanAdapter(viewModel, this);
        rvSaved.setAdapter(savedAdapter);
    }

    private void setupForm(View view, long userId) {
        EditText etCity = view.findViewById(R.id.et_tp_city);
        EditText etReq  = view.findViewById(R.id.et_tp_required_places);
        if (getArguments() != null) {
            String city = getArguments().getString(ARG_CITY, "");
            String req  = getArguments().getString(ARG_REQ, "");
            if (!city.isEmpty()) etCity.setText(city);
            if (!req.isEmpty())  etReq.setText(req);
        }

        SeekBar sbBudget   = view.findViewById(R.id.seekbar_tp_budget);
        SeekBar sbDuration = view.findViewById(R.id.seekbar_tp_duration);
        TextView tvBudget  = view.findViewById(R.id.tv_tp_budget_value);
        TextView tvDur     = view.findViewById(R.id.tv_tp_duration_value);
        MaterialButton btnGenerate = view.findViewById(R.id.btn_tp_generate);
        View progressGenerate = view.findViewById(R.id.progress_tp_generate);

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

        btnGenerate.setOnClickListener(v -> {
            String city = etCity.getText().toString().trim();
            if (city.isEmpty()) {
                Toast.makeText(getContext(), "Entrez une destination", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mode : lieux uniquement ou lieux + suggestions
            boolean onlyRequired = ((RadioGroup) view.findViewById(R.id.rg_places_mode))
                    .getCheckedRadioButtonId() == R.id.rb_places_only;

            Set<String> activities = new LinkedHashSet<>();
            if (!onlyRequired) {
                if (((CheckBox) view.findViewById(R.id.cb_culture)).isChecked())      activities.add("Culture");
                if (((CheckBox) view.findViewById(R.id.cb_restauration)).isChecked()) activities.add("Restauration");
                if (((CheckBox) view.findViewById(R.id.cb_loisirs)).isChecked())      activities.add("Loisirs");
                if (((CheckBox) view.findViewById(R.id.cb_decouverte)).isChecked())   activities.add("Découverte");
                if (((CheckBox) view.findViewById(R.id.cb_shopping)).isChecked())     activities.add("Shopping");
            }

            String reqPlaces = etReq.getText().toString().trim();

            if (!onlyRequired && activities.isEmpty()) {
                Toast.makeText(getContext(), "Sélectionnez au moins une activité", Toast.LENGTH_SHORT).show();
                return;
            }
            if (onlyRequired && reqPlaces.isEmpty()) {
                Toast.makeText(getContext(), "Entrez au moins un lieu spécifique", Toast.LENGTH_SHORT).show();
                return;
            }

            int budget   = Math.max(10, sbBudget.getProgress());
            int duration = sbDuration.getProgress() + 1;

            String effort = "Facile";
            int rbId = ((RadioGroup) view.findViewById(R.id.rg_tp_effort)).getCheckedRadioButtonId();
            if (rbId == R.id.rb_modere) effort = "Modéré";
            else if (rbId == R.id.rb_intense) effort = "Intense";

            btnGenerate.setEnabled(false);
            btnGenerate.setText("Génération...");
            progressGenerate.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), "Génération en cours…", Toast.LENGTH_SHORT).show();

            viewModel.generatePlans(userId, city, activities, budget, duration, effort, reqPlaces, null, createdCount -> {
                if (!isAdded() || getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    if (!isAdded() || getActivity() == null) return;
                    
                    btnGenerate.setEnabled(true);
                    btnGenerate.setText("Générer mes parcours");
                    progressGenerate.setVisibility(View.GONE);

                    if (createdCount <= 0) {
                        Toast.makeText(getContext(),
                                "Aucun lieu trouvé. Essayez une ville plus précise ou d'autres activités.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Scroll vers les résultats
                    androidx.core.widget.NestedScrollView scroll = view.findViewById(R.id.scroll_tp);
                    if (scroll != null && sectionResults != null) {
                        scroll.post(() -> scroll.smoothScrollTo(0, sectionResults.getTop()));
                    }
                    Toast.makeText(getContext(), createdCount + " parcours généré(s) !", Toast.LENGTH_SHORT).show();
                });
            });
        });

        view.findViewById(R.id.btn_tp_delete_all).setOnClickListener(v -> {
            if (getContext() == null) return;
            new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Effacer les résultats ?")
                    .setMessage("Ceci supprimera les parcours non sauvegardés de cette liste.")
                    .setPositiveButton("Effacer", (d, w) -> viewModel.deleteAllUnsaved(userId))
                    .setNegativeButton("Annuler", null)
                    .show();
        });
    }

    public static class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.PVH> {
        private List<TravelPlan> plans = new ArrayList<>();
        private final TravelPathViewModel viewModel;
        private final Fragment fragment;

        public PlanAdapter(TravelPathViewModel vm, Fragment f) {
            this.viewModel = vm;
            this.fragment  = f;
        }

        static class PVH extends RecyclerView.ViewHolder {
            View vColor;
            TextView tvBadge, tvCity, tvLiked, tvBudget, tvDuration, tvEffort, tvActivities;
            android.widget.Button btnDetail, btnDelete;

            PVH(View v) {
                super(v);
                vColor       = v.findViewById(R.id.v_plan_color);
                tvBadge      = v.findViewById(R.id.tv_plan_type_badge);
                tvCity       = v.findViewById(R.id.tv_plan_city);
                tvLiked      = v.findViewById(R.id.tv_plan_liked);
                tvBudget     = v.findViewById(R.id.tv_plan_budget);
                tvDuration   = v.findViewById(R.id.tv_plan_duration);
                tvEffort     = v.findViewById(R.id.tv_plan_effort);
                tvActivities = v.findViewById(R.id.tv_plan_activities);
                btnDetail    = v.findViewById(R.id.btn_plan_detail);
                btnDelete    = v.findViewById(R.id.btn_plan_delete);
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
                SessionManager session = new SessionManager(v.getContext());
                if (!session.isLoggedIn()) {
                    Toast.makeText(v.getContext(), "Connectez-vous pour sauvegarder un parcours", Toast.LENGTH_SHORT).show();
                    return;
                }

                p.liked = !p.liked;
                p.saved = p.liked;
                viewModel.saveLikeAndSave(p);
                h.tvLiked.setText(p.liked ? "♥" : "♡");
            });

            h.btnDetail.setOnClickListener(v -> {
                if (fragment.isAdded() && fragment.getActivity() != null) {
                    fragment.getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, PlanDetailFragment.newInstance(p.id))
                            .addToBackStack(null)
                            .commit();
                }
            });

            h.btnDelete.setOnClickListener(v -> {
                if (v.getContext() == null) return;
                new android.app.AlertDialog.Builder(v.getContext())
                        .setTitle("Supprimer ce parcours ?")
                        .setPositiveButton("Supprimer", (d, w) -> viewModel.deletePlan(p.id))
                        .setNegativeButton("Annuler", null)
                        .show();
            });
        }

        @Override public int getItemCount() { return plans.size(); }

        void setPlans(List<TravelPlan> list) {
            this.plans = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
