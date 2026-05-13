package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.NotificationPreference;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.LoginActivity;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private ActivityResultLauncher<String> avatarPickerLauncher;
    private String currentAvatarUri = "";
    private ImageView ivAvatar;
    private EditText etBio;
    private SessionManager session;
    private SharedViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null || ivAvatar == null) return;
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException ignored) {}
                    currentAvatarUri = uri.toString();
                    ivAvatar.setVisibility(View.VISIBLE);
                    Glide.with(this).load(uri).centerCrop().into(ivAvatar);
                    // Auto-save avatar
                    String bio = etBio != null ? etBio.getText().toString().trim() : "";
                    viewModel.updateUserProfile(session.getUserId(), currentAvatarUri, bio);
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        session = new SessionManager(requireContext());
        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        // ── Infos utilisateur ──────────────────────────────────────────────
        TextView tvName   = view.findViewById(R.id.tv_profile_name);
        TextView tvHandle = view.findViewById(R.id.tv_profile_handle);
        TextView tvAvatar = view.findViewById(R.id.tv_profile_avatar);
        ivAvatar          = view.findViewById(R.id.iv_profile_avatar);
        etBio             = view.findViewById(R.id.et_bio);

        if (session.isLoggedIn()) {
            String username = session.getUsername();
            tvName.setText(username);
            tvHandle.setText("@" + username.toLowerCase().replace(" ", "_"));
            tvAvatar.setText(username.length() > 0
                    ? String.valueOf(username.charAt(0)).toUpperCase() : "?");

            // Charger avatar + bio depuis la DB
            viewModel.getUserByLogin(username, user -> {
                if (user == null || !isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (user.avatarUri != null && !user.avatarUri.isEmpty()) {
                        currentAvatarUri = user.avatarUri;
                        ivAvatar.setVisibility(View.VISIBLE);
                        Glide.with(this).load(Uri.parse(user.avatarUri)).centerCrop().into(ivAvatar);
                    }
                    if (user.bio != null && !user.bio.isEmpty() && etBio != null) {
                        etBio.setText(user.bio);
                    }
                });
            });
            // Charger bio depuis Firestore (mise à jour si plus récente)
            FirebaseRepository.getInstance().loadUserProfile(username, data -> {
                if (data == null || !isAdded()) return;
                String firebaseBio = data.get("bio") instanceof String ? (String) data.get("bio") : null;
                if (firebaseBio != null && !firebaseBio.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded() || etBio == null) return;
                        if (etBio.getText().toString().trim().isEmpty()) {
                            etBio.setText(firebaseBio);
                        }
                    });
                }
            });
        } else {
            tvName.setText("Anonyme");
            tvHandle.setText("Mode anonyme");
            tvAvatar.setText("?");
            etBio.setEnabled(false);
            etBio.setHint("Connectez-vous pour rédiger une bio");
        }

        // ── Avatar — clic pour changer la photo ───────────────────────────
        view.findViewById(R.id.frame_avatar).setOnClickListener(v -> {
            if (!session.isLoggedIn()) return;
            avatarPickerLauncher.launch("image/*");
        });

        // ── Sauvegarde bio ────────────────────────────────────────────────
        view.findViewById(R.id.btn_save_bio).setOnClickListener(v -> {
            if (!session.isLoggedIn()) return;
            String bio = etBio.getText().toString().trim();
            viewModel.updateUserProfile(session.getUserId(), currentAvatarUri, bio);
            FirebaseRepository.getInstance().saveUserProfile(session.getUsername(), bio);
            Toast.makeText(getContext(), "Profil mis à jour ✓", Toast.LENGTH_SHORT).show();
        });

        // ── Stats : photos publiées ────────────────────────────────────────
        TextView tvStatPhotos = view.findViewById(R.id.tv_stat_photos);
        TextView tvStatLikes  = view.findViewById(R.id.tv_stat_likes);
        TextView tvStatGroups = view.findViewById(R.id.tv_stat_groups);

        viewModel.getAllPhotos().observe(getViewLifecycleOwner(), photos -> {
            if (photos == null) return;
            int myPhotos = 0, myLikes = 0;
            for (Photo p : photos) {
                if (session.getUsername() != null && session.getUsername().equals(p.getAuthor())) {
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

        // ── Stats groupes + liste preview ──────────────────────────────────
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

        view.findViewById(R.id.layout_alerts_preview).setOnClickListener(v -> {
            if (getActivity() != null)
                getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new NotificationPreferencesFragment())
                        .addToBackStack(null)
                        .commit();
        });

        view.findViewById(R.id.btn_groups).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new GroupsFragment())
                .addToBackStack(null)
                .commit()
        );

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

        void setPhotos(List<Photo> list) { this.photos = list != null ? list : new ArrayList<>(); notifyDataSetChanged(); }
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

        @Override public int getItemCount() { return Math.min(groups.size(), 3); }

        void setGroups(List<Group> g) { this.groups = g != null ? g : new ArrayList<>(); notifyDataSetChanged(); }
    }
}
