package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.travelshare.data.models.TravelPlan;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.LoginActivity;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.example.travelshare.viewmodels.TravelPathViewModel;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private ActivityResultLauncher<String> avatarPickerLauncher;
    private String currentAvatarUri = "";
    private ImageView ivAvatar;
    private EditText etBio;
    private SessionManager session;
    private SharedViewModel viewModel;
    private TravelPathViewModel pathViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null || ivAvatar == null) return;
                    
                    Toast.makeText(getContext(), "Upload de l'avatar...", Toast.LENGTH_SHORT).show();
                    com.example.travelshare.utils.CloudinaryHelper.uploadImage(uri, new com.example.travelshare.utils.CloudinaryHelper.UploadListener() {
                        @Override
                        public void onSuccess(String publicUrl) {
                            requireActivity().runOnUiThread(() -> {
                                currentAvatarUri = publicUrl;
                                ivAvatar.setVisibility(View.VISIBLE);
                                Glide.with(ProfileFragment.this).load(publicUrl).centerCrop().into(ivAvatar);
                                
                                String bio = etBio != null ? etBio.getText().toString().trim() : "";
                                viewModel.updateUserProfile(session.getUserId(), currentAvatarUri, bio);
                                FirebaseRepository.getInstance().saveUserProfile(session.getUsername(), bio, currentAvatarUri);
                                Toast.makeText(getContext(), "Avatar mis à jour !", Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            requireActivity().runOnUiThread(() -> 
                                Toast.makeText(getContext(), "Erreur upload : " + message, Toast.LENGTH_LONG).show());
                        }
                    });
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
        pathViewModel = new ViewModelProvider(this).get(TravelPathViewModel.class);

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

            FirebaseRepository.getInstance().getUserProfile(username, doc -> {
                if (doc == null || !doc.exists() || !isAdded()) return;
                String firebaseBio = doc.getString("bio");
                if (firebaseBio != null && !firebaseBio.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded() || etBio == null) return;
                        if (etBio.getText().toString().trim().isEmpty()) {
                            etBio.setText(firebaseBio);
                        }
                    });
                }
            });
        }

        view.findViewById(R.id.frame_avatar).setOnClickListener(v -> {
            if (!session.isLoggedIn()) return;
            avatarPickerLauncher.launch("image/*");
        });

        view.findViewById(R.id.btn_save_bio).setOnClickListener(v -> {
            if (!session.isLoggedIn()) return;
            String bio = etBio.getText().toString().trim();
            viewModel.updateUserProfile(session.getUserId(), currentAvatarUri, bio);
            FirebaseRepository.getInstance().saveUserProfile(session.getUsername(), bio, currentAvatarUri);
            Toast.makeText(getContext(), "Profil mis à jour ✓", Toast.LENGTH_SHORT).show();
        });

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

        // Saved Plans
        RecyclerView rvSavedPlans = view.findViewById(R.id.rv_saved_plans);
        rvSavedPlans.setLayoutManager(new LinearLayoutManager(getContext()));
        TravelPathFragment.PlanAdapter plansAdapter = new TravelPathFragment.PlanAdapter(pathViewModel, this);
        rvSavedPlans.setAdapter(plansAdapter);
        if (session.isLoggedIn()) {
            pathViewModel.getSavedPlansForUser(session.getUserId())
                    .observe(getViewLifecycleOwner(), plansAdapter::setPlans);
        }

        RecyclerView rvGroups = view.findViewById(R.id.rv_profile_groups);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        ProfileGroupAdapter groupAdapter = new ProfileGroupAdapter();
        rvGroups.setAdapter(groupAdapter);
        if (session.isLoggedIn()) {
            viewModel.getGroupsForUser(session.getUserId()).observe(getViewLifecycleOwner(), groups -> {
                tvStatGroups.setText(groups != null ? String.valueOf(groups.size()) : "0");
                groupAdapter.setGroups(groups);
            });
        }

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
