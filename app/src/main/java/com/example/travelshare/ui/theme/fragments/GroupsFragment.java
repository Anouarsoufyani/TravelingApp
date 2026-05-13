package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.travelshare.R;
import com.example.travelshare.data.models.AppNotification;
import com.example.travelshare.data.models.Group;
import com.example.travelshare.data.models.GroupMember;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupsFragment extends Fragment {

    private static final int TAB_MY   = 0;
    private static final int TAB_DISC = 1;
    private int currentTab = TAB_MY;

    private SharedViewModel viewModel;
    private SessionManager session;
    private GroupAdapter adapter;
    private LiveData<List<Group>> currentSource;

    private TextView tabMyGroups, tabDiscover;
    private View layoutSearch;
    private EditText etSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        session   = new SessionManager(requireContext());

        tabMyGroups  = view.findViewById(R.id.tab_my_groups);
        tabDiscover  = view.findViewById(R.id.tab_discover);
        layoutSearch = view.findViewById(R.id.layout_search);
        etSearch     = view.findViewById(R.id.et_search_groups);

        RecyclerView rv = view.findViewById(R.id.rv_groups);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GroupAdapter(viewModel, session, this);
        rv.setAdapter(adapter);

        tabMyGroups.setOnClickListener(v -> switchTab(TAB_MY));
        tabDiscover.setOnClickListener(v -> switchTab(TAB_DISC));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                observeGroups(q.isEmpty() ? viewModel.getAllGroups() : viewModel.searchGroups(q));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.btn_create_group).setOnClickListener(v -> {
            if (!session.isLoggedIn()) {
                Toast.makeText(getContext(), "Connectez-vous pour créer un groupe.", Toast.LENGTH_SHORT).show();
                return;
            }
            showCreateDialog();
        });

        switchTab(TAB_MY);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Rafraîchir la session et les données à chaque retour (changement de compte)
        session = new SessionManager(requireContext());
        adapter.setSession(session);
        switchTab(currentTab);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        boolean myTab = (tab == TAB_MY);

        tabMyGroups.setTextColor(requireContext().getResources().getColor(
                myTab ? R.color.teal : R.color.ink_muted, null));
        tabMyGroups.setTypeface(null, myTab ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tabDiscover.setTextColor(requireContext().getResources().getColor(
                myTab ? R.color.ink_muted : R.color.teal, null));
        tabDiscover.setTypeface(null, myTab ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);

        layoutSearch.setVisibility(myTab ? View.GONE : View.VISIBLE);
        if (myTab) etSearch.setText("");

        adapter.setMode(myTab ? GroupAdapter.MODE_MY : GroupAdapter.MODE_DISCOVER);
        adapter.clearMemberships();

        if (myTab) {
            // Sync des statuts acceptés depuis Firestore (cas : demande acceptée sur un autre appareil)
            if (session.isLoggedIn()) {
                viewModel.syncMyMemberStatuses(session.getUsername());
                observeGroups(viewModel.getGroupsForUser(session.getUserId()));
            } else {
                // Non connecté → liste vide, jamais getAllGroups()
                if (currentSource != null) currentSource.removeObservers(getViewLifecycleOwner());
                currentSource = null;
                adapter.setGroups(new ArrayList<>());
            }
        } else {
            // Découvrir : sync Firestore → Room puis afficher tous les groupes
            viewModel.syncGroupsFromFirestore();
            observeGroups(viewModel.getAllGroups());
        }
    }

    private void observeGroups(LiveData<List<Group>> source) {
        if (currentSource != null) currentSource.removeObservers(getViewLifecycleOwner());
        currentSource = source;
        currentSource.observe(getViewLifecycleOwner(), groups -> {
            adapter.setGroups(groups);
            // En mode Découvrir, résoudre les statuts de membership après avoir reçu la liste
            if (currentTab == TAB_DISC && session.isLoggedIn() && groups != null) {
                for (Group g : groups) {
                    viewModel.getMembership(session.getUserId(), g.id, m -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() ->
                                adapter.putMembership(g.id, m != null ? m.status : null));
                    });
                }
            }
        });
    }

    private void showCreateDialog() {
        EditText etName = new EditText(getContext());
        etName.setHint("Nom du groupe");
        EditText etDesc = new EditText(getContext());
        etDesc.setHint("Description (optionnel)");
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);
        layout.addView(etName);
        layout.addView(etDesc);

        new AlertDialog.Builder(requireContext())
                .setTitle("Créer un groupe")
                .setView(layout)
                .setPositiveButton("Créer", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Group g = new Group();
                    g.name = name;
                    g.description = etDesc.getText().toString().trim();
                    g.creatorId = session.getUserId();
                    viewModel.insertGroup(g);
                    FirebaseRepository.getInstance().saveGroup(name, g.description, session.getUsername());
                    Toast.makeText(getContext(), "Groupe \"" + name + "\" créé !", Toast.LENGTH_SHORT).show();
                    switchTab(TAB_MY);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ── Adapter ───────────────────────────────────────────────────────────

    static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GVH> {

        static final int MODE_MY       = 0;
        static final int MODE_DISCOVER = 1;

        private List<Group> groups = new ArrayList<>();
        private int mode = MODE_MY;
        private SessionManager session;
        private final SharedViewModel viewModel;
        private final Fragment fragment;
        // groupId → status ("MEMBER"/"PENDING"/null) pour le user courant
        private final Map<Long, String> memberships = new HashMap<>();

        GroupAdapter(SharedViewModel vm, SessionManager s, Fragment f) {
            this.viewModel = vm;
            this.session   = s;
            this.fragment  = f;
        }

        void setSession(SessionManager s) { this.session = s; }
        void setMode(int m)               { mode = m; notifyDataSetChanged(); }

        void clearMemberships() {
            memberships.clear();
            notifyDataSetChanged();
        }

        void putMembership(long groupId, String status) {
            memberships.put(groupId, status);
            notifyDataSetChanged();
        }

        void setGroups(List<Group> g) {
            groups = g != null ? g : new ArrayList<>();
            notifyDataSetChanged();
        }

        static class GVH extends RecyclerView.ViewHolder {
            TextView tvAvatar, tvName, tvDesc, tvAction, tvChat, tvUnreadBadge;
            GVH(View v) {
                super(v);
                tvAvatar      = v.findViewById(R.id.tv_group_avatar);
                tvName        = v.findViewById(R.id.tv_group_name);
                tvDesc        = v.findViewById(R.id.tv_group_desc);
                tvAction      = v.findViewById(R.id.tv_group_action);
                tvChat        = v.findViewById(R.id.tv_group_chat);
                tvUnreadBadge = v.findViewById(R.id.tv_unread_badge);
                View badge = v.findViewById(R.id.tv_pending_badge);
                if (badge != null) badge.setVisibility(View.GONE);
            }
        }

        @NonNull @Override
        public GVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group, parent, false);
            return new GVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull GVH h, int position) {
            Group g = groups.get(position);
            boolean isCreator = session.isLoggedIn() && g.creatorId == session.getUserId();

            String initial = (g.name != null && !g.name.isEmpty())
                    ? String.valueOf(g.name.charAt(0)).toUpperCase() : "G";
            h.tvAvatar.setText(initial);
            h.tvName.setText(g.name);
            h.tvDesc.setText(g.description != null && !g.description.isEmpty()
                    ? g.description : "Groupe privé");

            if (mode == MODE_MY) {
                bindMyGroup(h, g, isCreator);
            } else {
                bindDiscoverGroup(h, g, isCreator);
            }
        }

        private void bindMyGroup(GVH h, Group g, boolean isCreator) {
            h.tvAction.setText(isCreator ? "👑 Admin" : "Membre ✓");
            h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(
                    isCreator ? R.color.terracotta : R.color.teal, null));
            h.tvAction.setOnClickListener(null);

            h.tvChat.setVisibility(View.VISIBLE);
            h.tvChat.setOnClickListener(v -> {
                viewModel.markGroupMessagesRead(session.getUserId(), g.id);
                h.tvUnreadBadge.setVisibility(View.GONE);
                openChat(v, g);
            });

            // Badge messages non lus
            if (session.isLoggedIn()) {
                viewModel.getUnreadCountForGroup(session.getUserId(), g.id)
                        .observe((androidx.lifecycle.LifecycleOwner) fragment, count -> {
                    if (count != null && count > 0) {
                        h.tvUnreadBadge.setVisibility(View.VISIBLE);
                        h.tvUnreadBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                    } else {
                        h.tvUnreadBadge.setVisibility(View.GONE);
                    }
                });
            }
        }

        private void bindDiscoverGroup(GVH h, Group g, boolean isCreator) {
            h.tvChat.setVisibility(View.GONE);

            if (isCreator) {
                h.tvAction.setText("👑 Mon groupe");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources()
                        .getColor(R.color.terracotta, null));
                h.tvAction.setOnClickListener(v -> openChat(v, g));
                return;
            }

            String status = memberships.get(g.id);

            if ("MEMBER".equals(status)) {
                h.tvAction.setText("Membre ✓");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources()
                        .getColor(R.color.teal, null));
                h.tvAction.setOnClickListener(v ->
                        new AlertDialog.Builder(v.getContext())
                                .setTitle("Quitter " + g.name + " ?")
                                .setPositiveButton("Quitter", (d, w) -> {
                                    viewModel.rejectOrLeaveGroup(g.id, session.getUserId());
                                    FirebaseRepository.getInstance().deleteGroupMember(g.name, session.getUsername());
                                    memberships.put(g.id, null);
                                    notifyDataSetChanged();
                                })
                                .setNegativeButton("Annuler", null)
                                .show());
            } else if ("PENDING".equals(status)) {
                h.tvAction.setText("⏳ En attente");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources()
                        .getColor(R.color.ink_muted, null));
                h.tvAction.setOnClickListener(v ->
                        Toast.makeText(v.getContext(), "Demande envoyée, en attente d'approbation.", Toast.LENGTH_SHORT).show());
            } else {
                h.tvAction.setText("Demander →");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources()
                        .getColor(R.color.navy, null));
                h.tvAction.setOnClickListener(v -> {
                    if (!session.isLoggedIn()) {
                        Toast.makeText(v.getContext(), "Connectez-vous pour rejoindre un groupe.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.requestJoinGroup(g.id, session.getUserId(), session.getUsername());
                    FirebaseRepository.getInstance().saveGroupMember(g.name, session.getUsername(), "PENDING");
                    memberships.put(g.id, "PENDING");
                    notifyDataSetChanged();
                    Toast.makeText(v.getContext(), "Demande envoyée !", Toast.LENGTH_SHORT).show();
                    // Notif au créateur
                    AppNotification notif = new AppNotification();
                    notif.targetUserId = g.creatorId;
                    notif.type    = "JOIN_REQUEST";
                    notif.message = session.getUsername() + " souhaite rejoindre \"" + g.name + "\"";
                    notif.groupId = g.id;
                    notif.date    = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                    viewModel.insertAppNotification(notif);
                });
            }
        }

        private void openChat(View v, Group g) {
            androidx.fragment.app.FragmentActivity activity =
                    (androidx.fragment.app.FragmentActivity) v.getContext();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container,
                            GroupChatFragment.newInstance(g.id, g.name, g.creatorId))
                    .addToBackStack(null)
                    .commit();
        }

        @Override public int getItemCount() { return groups.size(); }
    }
}
