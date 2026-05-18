package com.example.travelshare.ui.theme.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.travelshare.R;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.Photo;
import com.example.travelshare.data.models.TravelPlan;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.ui.LoginActivity;
import com.example.travelshare.ui.PhotoDetailActivity;
import com.example.travelshare.ui.UserProfileActivity;
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
    private List<Group> memberGroups = new ArrayList<>();

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
        viewModel.syncPhotosFromFirestore();

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
        TextView tvStatFollowing = view.findViewById(R.id.tv_stat_following);
        TextView tvStatFollowers = view.findViewById(R.id.tv_stat_followers);
        TextView tabPosts = view.findViewById(R.id.tab_profile_posts);
        TextView tabPaths = view.findViewById(R.id.tab_profile_paths);
        TextView tvContentEmpty = view.findViewById(R.id.tv_profile_content_empty);
        RecyclerView rvPosts = view.findViewById(R.id.rv_profile_posts);
        RecyclerView rvPaths = view.findViewById(R.id.rv_profile_paths);

        ProfilePostAdapter postAdapter = new ProfilePostAdapter();
        ProfilePlanAdapter planAdapter = new ProfilePlanAdapter(this);
        rvPosts.setLayoutManager(new GridLayoutManager(getContext(), 3));
        rvPosts.setAdapter(postAdapter);
        rvPaths.setLayoutManager(new LinearLayoutManager(getContext()));
        rvPaths.setAdapter(planAdapter);

        View.OnClickListener showPosts = v -> selectProfileTab(true, tabPosts, tabPaths,
                rvPosts, rvPaths, tvContentEmpty, postAdapter, planAdapter);
        View.OnClickListener showPaths = v -> selectProfileTab(false, tabPosts, tabPaths,
                rvPosts, rvPaths, tvContentEmpty, postAdapter, planAdapter);
        tabPosts.setOnClickListener(showPosts);
        tabPaths.setOnClickListener(showPaths);

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

        if (session.isLoggedIn()) {
            viewModel.getPhotosByAuthor(session.getUsername()).observe(getViewLifecycleOwner(), photos -> {
                postAdapter.setPhotos(photos);
                updateProfileEmpty(tabPosts, rvPosts, rvPaths, tvContentEmpty, postAdapter, planAdapter);
            });
            pathViewModel.getSavedPlansForUser(session.getUserId()).observe(getViewLifecycleOwner(), plans -> {
                planAdapter.setPlans(plans);
                updateProfileEmpty(tabPosts, rvPosts, rvPaths, tvContentEmpty, postAdapter, planAdapter);
            });
        }

        if (session.isLoggedIn()) {
            FirebaseRepository.getInstance().getFollowing(session.getUsername(), following -> {
                List<String> safeList = following != null ? following : new ArrayList<>();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        tvStatFollowing.setText(String.valueOf(safeList.size())));
            });
            FirebaseRepository.getInstance().getFollowers(session.getUsername(), followers -> {
                List<String> safeList = followers != null ? followers : new ArrayList<>();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        tvStatFollowers.setText(String.valueOf(safeList.size())));
            });
        }

        view.findViewById(R.id.layout_stat_following).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour voir vos suivis", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseRepository.getInstance().getFollowing(session.getUsername(),
                    users -> showUserList("Utilisateurs suivis", users));
        });

        view.findViewById(R.id.layout_stat_followers).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour voir vos followers", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseRepository.getInstance().getFollowers(session.getUsername(),
                    users -> showUserList("Followers", users));
        });

        RecyclerView rvGroups = view.findViewById(R.id.rv_profile_groups);
        rvGroups.setLayoutManager(new LinearLayoutManager(getContext()));
        ProfileGroupAdapter groupAdapter = new ProfileGroupAdapter();
        rvGroups.setAdapter(groupAdapter);
        if (session.isLoggedIn()) {
            FirebaseRepository.getInstance().getMyMemberGroups(session.getUsername(), groups -> {
                List<Group> safeGroups = groups != null ? groups : new ArrayList<>();
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    memberGroups = safeGroups;
                    tvStatGroups.setText(String.valueOf(memberGroups.size()));
                    groupAdapter.setGroups(memberGroups);
                });
            });
        }

        view.findViewById(R.id.layout_stat_groups).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour voir vos groupes", Toast.LENGTH_SHORT).show();
                return;
            }
            showGroupList("Groupes", memberGroups);
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

    private void selectProfileTab(boolean postsSelected, TextView tabPosts, TextView tabPaths,
                                  RecyclerView rvPosts, RecyclerView rvPaths, TextView tvEmpty,
                                  ProfilePostAdapter postAdapter, ProfilePlanAdapter planAdapter) {
        int selectedBg = getResources().getColor(R.color.coral, null);
        int selectedText = getResources().getColor(R.color.default_white, null);
        int mutedText = getResources().getColor(R.color.sheet_muted, null);

        tabPosts.setBackgroundResource(postsSelected ? R.drawable.bg_pill_solid : 0);
        tabPaths.setBackgroundResource(postsSelected ? 0 : R.drawable.bg_pill_solid);
        tabPosts.setBackgroundTintList(postsSelected ? ColorStateList.valueOf(selectedBg) : null);
        tabPaths.setBackgroundTintList(postsSelected ? null : ColorStateList.valueOf(selectedBg));
        tabPosts.setTextColor(postsSelected ? selectedText : mutedText);
        tabPaths.setTextColor(postsSelected ? mutedText : selectedText);
        rvPosts.setVisibility(postsSelected ? View.VISIBLE : View.GONE);
        rvPaths.setVisibility(postsSelected ? View.GONE : View.VISIBLE);
        updateProfileEmpty(tabPosts, rvPosts, rvPaths, tvEmpty, postAdapter, planAdapter);
    }

    private void updateProfileEmpty(TextView tabPosts, RecyclerView rvPosts, RecyclerView rvPaths,
                                    TextView tvEmpty, ProfilePostAdapter postAdapter,
                                    ProfilePlanAdapter planAdapter) {
        boolean postsVisible = rvPosts.getVisibility() == View.VISIBLE;
        int count = postsVisible ? postAdapter.getItemCount() : planAdapter.getItemCount();
        tvEmpty.setText(postsVisible ? "Aucun post pour le moment." : "Aucun parcours enregistré.");
        tvEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void showUserList(String title, List<String> users) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            if (users == null || users.isEmpty()) {
                Toast.makeText(getContext(), "Aucun utilisateur à afficher", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] names = users.toArray(new String[0]);
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setItems(names, (dialog, which) -> {
                        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USERNAME, names[which]);
                        startActivity(intent);
                    })
                    .setNegativeButton("Fermer", null)
                    .show();
        });
    }

    private void showGroupList(String title, List<Group> groups) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            if (groups == null || groups.isEmpty()) {
                Toast.makeText(getContext(), "Aucun groupe à afficher", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] names = new String[groups.size()];
            for (int i = 0; i < groups.size(); i++) {
                names[i] = groups.get(i).name;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setItems(names, (dialog, which) -> {
                        Group group = groups.get(which);
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, GroupChatFragment.newInstance(
                                        group.id, group.name, group.creatorId, group.creatorUsername))
                                .addToBackStack(null)
                                .commit();
                    })
                    .setNegativeButton("Fermer", null)
                    .show();
        });
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

    static class ProfilePostAdapter extends RecyclerView.Adapter<ProfilePostAdapter.PVH> {
        private List<Photo> photos = new ArrayList<>();

        static class PVH extends RecyclerView.ViewHolder {
            View placeholder;
            ImageView image;

            PVH(View v) {
                super(v);
                placeholder = v.findViewById(R.id.profile_post_placeholder);
                image = v.findViewById(R.id.iv_profile_post);
            }
        }

        @NonNull @Override
        public PVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_profile_post_square, parent, false);
            return new PVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PVH h, int position) {
            Photo photo = photos.get(position);
            List<String> uris = photo.getImageUriList();
            String firstUri = !uris.isEmpty() ? uris.get(0) : null;

            if (firstUri != null) {
                h.image.setVisibility(View.VISIBLE);
                h.placeholder.setVisibility(View.GONE);
                Glide.with(h.itemView.getContext())
                        .load(Uri.parse(firstUri))
                        .centerCrop()
                        .into(h.image);
            } else {
                Glide.with(h.itemView.getContext()).clear(h.image);
                h.image.setVisibility(View.GONE);
                h.placeholder.setVisibility(View.VISIBLE);
            }

            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), PhotoDetailActivity.class);
                intent.putExtra(PhotoDetailActivity.EXTRA_PHOTO_ID, photo.getId());
                intent.putExtra(PhotoDetailActivity.EXTRA_TITLE, photo.getTitle());
                intent.putExtra(PhotoDetailActivity.EXTRA_AUTHOR, photo.getAuthor());
                intent.putExtra(PhotoDetailActivity.EXTRA_DATE, photo.getDate());
                intent.putExtra(PhotoDetailActivity.EXTRA_LOCATION, photo.getLocation());
                intent.putExtra(PhotoDetailActivity.EXTRA_CATEGORY, photo.getCategory());
                intent.putExtra(PhotoDetailActivity.EXTRA_TAGS, photo.getTags());
                intent.putExtra(PhotoDetailActivity.EXTRA_LIKES, photo.getLikes());
                intent.putExtra(PhotoDetailActivity.EXTRA_LAT, photo.getLatitude());
                intent.putExtra(PhotoDetailActivity.EXTRA_LNG, photo.getLongitude());
                intent.putExtra(PhotoDetailActivity.EXTRA_IMAGE_URI, photo.getImageUri());
                intent.putExtra(PhotoDetailActivity.EXTRA_VOICE_URI, photo.getVoiceUri());
                v.getContext().startActivity(intent);
            });
        }

        @Override public int getItemCount() { return photos.size(); }

        void setPhotos(List<Photo> photos) {
            this.photos = photos != null ? photos : new ArrayList<>();
            notifyDataSetChanged();
        }
    }

    static class ProfilePlanAdapter extends RecyclerView.Adapter<ProfilePlanAdapter.PVH> {
        private List<TravelPlan> plans = new ArrayList<>();
        private final Fragment fragment;

        ProfilePlanAdapter(Fragment fragment) {
            this.fragment = fragment;
        }

        static class PVH extends RecyclerView.ViewHolder {
            View color;
            TextView city, meta, activities;

            PVH(View v) {
                super(v);
                color = v.findViewById(R.id.v_profile_plan_color);
                city = v.findViewById(R.id.tv_profile_plan_city);
                meta = v.findViewById(R.id.tv_profile_plan_meta);
                activities = v.findViewById(R.id.tv_profile_plan_activities);
            }
        }

        @NonNull @Override
        public PVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_profile_plan_row, parent, false);
            return new PVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PVH h, int position) {
            TravelPlan plan = plans.get(position);
            int color;
            String type;
            if ("economique".equals(plan.type)) {
                color = h.itemView.getContext().getResources().getColor(R.color.teal, null);
                type = "Économique";
            } else if ("confort".equals(plan.type)) {
                color = h.itemView.getContext().getResources().getColor(R.color.terracotta, null);
                type = "Confort";
            } else {
                color = h.itemView.getContext().getResources().getColor(R.color.coral, null);
                type = "Équilibré";
            }

            h.color.setBackgroundColor(color);
            h.city.setText(plan.city != null ? plan.city : "Parcours");
            h.meta.setText(type + " · " + plan.durationHours + " h · " + plan.budgetEur + " €");
            h.activities.setText(plan.activities != null ? plan.activities.replace(",", " · ") : "");
            h.itemView.setOnClickListener(v -> {
                if (!fragment.isAdded() || fragment.getActivity() == null) return;
                fragment.getActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, PlanDetailFragment.newInstance(plan.id))
                        .addToBackStack(null)
                        .commit();
            });
        }

        @Override public int getItemCount() { return plans.size(); }

        void setPlans(List<TravelPlan> plans) {
            this.plans = plans != null ? plans : new ArrayList<>();
            notifyDataSetChanged();
        }
    }
}
