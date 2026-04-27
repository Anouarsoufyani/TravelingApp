package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.ui.LoginActivity;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        SessionManager session = new SessionManager(requireContext());
        SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // ── Infos utilisateur ──────────────────────────────────────────────
        TextView tvName   = view.findViewById(R.id.tv_profile_name);
        TextView tvHandle = view.findViewById(R.id.tv_profile_handle);
        TextView tvAvatar = view.findViewById(R.id.tv_profile_avatar);

        if (session.isLoggedIn()) {
            String username = session.getUsername();
            tvName.setText(username);
            tvHandle.setText("@" + username.toLowerCase().replace(" ", "_"));
            tvAvatar.setText(username.length() > 0
                    ? String.valueOf(username.charAt(0)).toUpperCase() : "?");
        } else {
            tvName.setText("Anonyme");
            tvHandle.setText("Mode anonyme");
            tvAvatar.setText("?");
        }

        // ── Stats : photos publiées ────────────────────────────────────────
        TextView tvStatPhotos = view.findViewById(R.id.tv_stat_photos);
        TextView tvStatLikes  = view.findViewById(R.id.tv_stat_likes);
        TextView tvStatGroups = view.findViewById(R.id.tv_stat_groups);

        viewModel.getAllPhotos().observe(getViewLifecycleOwner(), photos -> {
            if (photos == null) return;
            int myPhotos = 0, myLikes = 0;
            for (com.example.travelshare.data.models.Photo p : photos) {
                if (session.getUsername().equals(p.getAuthor())) {
                    myPhotos++;
                    myLikes += p.getLikes();
                }
            }
            tvStatPhotos.setText(String.valueOf(myPhotos));
            tvStatLikes.setText(String.valueOf(myLikes));
        });

        // ── Mes publications (avec suppression) ────────────────────────────
        RecyclerView rvMyPhotos = view.findViewById(R.id.rv_my_photos);
        rvMyPhotos.setLayoutManager(new LinearLayoutManager(getContext()));
        MyPhotosAdapter myPhotosAdapter = new MyPhotosAdapter(viewModel);
        rvMyPhotos.setAdapter(myPhotosAdapter);
        if (session.isLoggedIn()) {
            viewModel.getPhotosByAuthor(session.getUsername())
                    .observe(getViewLifecycleOwner(), myPhotosAdapter::setPhotos);
        }

        // ── Stats groupes + liste preview (filtrés par user) ──────────────
        RecyclerView rvGroups = view.findViewById(R.id.rv_profile_groups);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        ProfileGroupAdapter groupAdapter = new ProfileGroupAdapter();
        rvGroups.setAdapter(groupAdapter);
        if (session.isLoggedIn()) {
            viewModel.getGroupsForUser(session.getUserId()).observe(getViewLifecycleOwner(), groups -> {
                tvStatGroups.setText(groups != null ? String.valueOf(groups.size()) : "0");
                groupAdapter.setGroups(groups);
            });
        } else {
            tvStatGroups.setText("0");
        }

        // ── Aperçu alertes ─────────────────────────────────────────────────
        TextView tvAlertsPreview = view.findViewById(R.id.tv_alerts_preview);
        if (session.isLoggedIn()) {
            viewModel.getPreferencesForUser(session.getUserId())
                    .observe(getViewLifecycleOwner(), prefs -> {
                        if (prefs == null || prefs.isEmpty()) {
                            tvAlertsPreview.setText("Aucune alerte configurée.");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (NotificationPreference p : prefs) {
                                sb.append("• ").append(p.type).append(" : ").append(p.value).append("\n");
                            }
                            tvAlertsPreview.setText(sb.toString().trim());
                        }
                    });
        }

        // Clic sur "Gérer mes alertes" → écran de gestion des préférences
        view.findViewById(R.id.layout_alerts_preview).setOnClickListener(v -> {
            if (getActivity() != null)
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new NotificationPreferencesFragment())
                        .addToBackStack(null)
                        .commit();
        });

        // ── Bouton Mes groupes ─────────────────────────────────────────────
        view.findViewById(R.id.btn_groups).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new GroupsFragment())
                .addToBackStack(null)
                .commit()
        );

        // ── Déconnexion ────────────────────────────────────────────────────
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            session.logoutUser();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return view;
    }

    // ── Adapter mes publications ───────────────────────────────────────────

    static class MyPhotosAdapter extends RecyclerView.Adapter<MyPhotosAdapter.MPVH> {
        private List<Photo> photos = new ArrayList<>();
        private final SharedViewModel viewModel;

        MyPhotosAdapter(SharedViewModel vm) { this.viewModel = vm; }

        static class MPVH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvMeta, btnDelete;
            MPVH(View v) {
                super(v);
                tvTitle   = v.findViewById(R.id.tv_my_photo_title);
                tvMeta    = v.findViewById(R.id.tv_my_photo_meta);
                btnDelete = v.findViewById(R.id.btn_delete_photo);
            }
        }

        @NonNull @Override
        public MPVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_photo, parent, false);
            return new MPVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MPVH h, int position) {
            Photo p = photos.get(position);
            h.tvTitle.setText(p.getTitle());
            h.tvMeta.setText(p.getLocation() + "  ·  " + p.getDate());
            h.btnDelete.setOnClickListener(v -> viewModel.deletePhoto(p.getId()));
        }

        @Override public int getItemCount() { return photos.size(); }

        void setPhotos(List<Photo> list) { this.photos = list; notifyDataSetChanged(); }
    }

    // ── Adapter mini groupes ───────────────────────────────────────────────

    static class ProfileGroupAdapter extends RecyclerView.Adapter<ProfileGroupAdapter.GVH> {
        private List<Group> groups = new ArrayList<>();

        static class GVH extends RecyclerView.ViewHolder {
            TextView tvName, tvCount;
            GVH(View v) {
                super(v);
                tvName  = v.findViewById(R.id.tv_pg_name);
                tvCount = v.findViewById(R.id.tv_pg_count);
            }
        }

        @NonNull @Override
        public GVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_group, parent, false);
            return new GVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull GVH h, int pos) {
            Group g = groups.get(pos);
            h.tvName.setText(g.name);
            h.tvCount.setText(g.description != null && !g.description.isEmpty() ? g.description : "Groupe");
        }

        @Override public int getItemCount() { return Math.min(groups.size(), 3); } // max 3 en aperçu

        void setGroups(List<Group> g) { this.groups = g; notifyDataSetChanged(); }
    }
}
