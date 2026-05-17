package com.example.travelshare.ui.theme.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
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
import com.google.firebase.firestore.ListenerRegistration;

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
    private ListenerRegistration groupsListener;
    private ListenerRegistration membershipsListener;
    private final List<Group> firebaseGroups = new ArrayList<>();
    private final Map<String, String> membershipsByName = new HashMap<>();

    private View layoutSearch;
    private EditText etSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groups, container, false);

        viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        session   = new SessionManager(requireContext());

        com.google.android.material.tabs.TabLayout tabs = view.findViewById(R.id.tabs_groups);
        layoutSearch = view.findViewById(R.id.layout_search);
        etSearch     = view.findViewById(R.id.et_search_groups);

        RecyclerView rv = view.findViewById(R.id.rv_groups);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GroupAdapter(viewModel, session, this);
        rv.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                switchTab(tab.getPosition());
            }
            @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderGroups();
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

        startFirestoreListeners();
        // Initial tab state already handled by switchTab or initial TabLayout setup
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        session = new SessionManager(requireContext());
        adapter.setSession(session);
        restartMembershipListener();
        renderGroups();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (groupsListener != null) { groupsListener.remove(); groupsListener = null; }
        if (membershipsListener != null) { membershipsListener.remove(); membershipsListener = null; }
    }

    private void startFirestoreListeners() {
        if (groupsListener != null) groupsListener.remove();
        groupsListener = FirebaseRepository.getInstance().listenToGroups(groups -> {
            firebaseGroups.clear();
            if (groups != null) firebaseGroups.addAll(groups);
            if (isAdded()) requireActivity().runOnUiThread(this::renderGroups);
        });
        restartMembershipListener();
    }

    private void restartMembershipListener() {
        if (membershipsListener != null) {
            membershipsListener.remove();
            membershipsListener = null;
        }
        membershipsByName.clear();
        if (!session.isLoggedIn()) {
            if (adapter != null) adapter.setMemberships(membershipsByName);
            return;
        }
        membershipsListener = FirebaseRepository.getInstance()
                .listenToMyGroupMemberships(session.getUsername(), statuses -> {
                    membershipsByName.clear();
                    if (statuses != null) membershipsByName.putAll(statuses);
                    if (isAdded()) requireActivity().runOnUiThread(this::renderGroups);
                });
    }

    private void switchTab(int tab) {
        currentTab = tab;
        boolean myTab = (tab == TAB_MY);

        layoutSearch.setVisibility(myTab ? View.GONE : View.VISIBLE);
        if (myTab) etSearch.setText("");

        adapter.setMode(myTab ? GroupAdapter.MODE_MY : GroupAdapter.MODE_DISCOVER);
        renderGroups();
    }

    private void renderGroups() {
        if (adapter == null) return;
        adapter.setMemberships(membershipsByName);
        if (!session.isLoggedIn() && currentTab == TAB_MY) {
            adapter.setGroups(new ArrayList<>());
            return;
        }

        String currentUsername = session.getUsername();
        String query = etSearch != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        List<Group> visible = new ArrayList<>();
        for (Group g : firebaseGroups) {
            boolean isCreator = isCreator(g, currentUsername);
            String status = membershipsByName.get(g.name);
            boolean isMember = "MEMBER".equals(status);
            boolean belongsToMe = isCreator || isMember;

            if (currentTab == TAB_MY && !belongsToMe) continue;
            if (currentTab == TAB_DISC && !query.isEmpty()) {
                String haystack = ((g.name != null ? g.name : "") + " "
                        + (g.description != null ? g.description : "")).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            visible.add(g);
        }
        adapter.setGroups(visible);
    }

    private static boolean isCreator(Group g, String username) {
        return g.creatorUsername != null
                && username != null
                && g.creatorUsername.equalsIgnoreCase(username);
    }

    private void showCreateDialog() {
        EditText etName = new EditText(getContext());
        etName.setHint("Nom du groupe");
        EditText etDesc = new EditText(getContext());
        etDesc.setHint("Description (optionnel)");
        RadioGroup rgVisibility = new RadioGroup(getContext());
        rgVisibility.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbPrivate = new RadioButton(getContext());
        rbPrivate.setText("Privé");
        rbPrivate.setId(View.generateViewId());
        RadioButton rbPublic = new RadioButton(getContext());
        rbPublic.setText("Public");
        rbPublic.setId(View.generateViewId());
        rgVisibility.addView(rbPrivate);
        rgVisibility.addView(rbPublic);
        rgVisibility.check(rbPrivate.getId());
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);
        layout.addView(etName);
        layout.addView(etDesc);
        layout.addView(rgVisibility);

        new AlertDialog.Builder(requireContext())
                .setTitle("Créer un groupe")
                .setView(layout)
                .setPositiveButton("Créer", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Group g = new Group();
                    g.name = name;
                    g.description = etDesc.getText().toString().trim();
                    g.creatorId = FirebaseRepository.getInstance().stableUserId(session.getUsername());
                    g.creatorUsername = session.getUsername();
                    g.visibility = rgVisibility.getCheckedRadioButtonId() == rbPublic.getId()
                            ? Group.VISIBILITY_PUBLIC : Group.VISIBILITY_PRIVATE;
                    FirebaseRepository.getInstance().saveGroup(
                            name, g.description, session.getUsername(), g.creatorId, g.visibility);
                    Toast.makeText(getContext(),
                            "Groupe " + ("PUBLIC".equals(g.visibility) ? "public" : "privé")
                                    + " \"" + name + "\" créé !",
                            Toast.LENGTH_SHORT).show();
                    switchTab(TAB_MY);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GVH> {

        static final int MODE_MY       = 0;
        static final int MODE_DISCOVER = 1;

        private List<Group> groups = new ArrayList<>();
        private int mode = MODE_MY;
        private SessionManager session;
        private final SharedViewModel viewModel;
        private final Fragment fragment;

        private final Map<String, String> memberships = new HashMap<>();

        GroupAdapter(SharedViewModel vm, SessionManager s, Fragment f) {
            this.viewModel = vm;
            this.session   = s;
            this.fragment  = f;
        }

        void setSession(SessionManager s) { this.session = s; }
        void setMode(int m)               { mode = m; notifyDataSetChanged(); }

        void setMemberships(Map<String, String> statuses) {
            memberships.clear();
            if (statuses != null) memberships.putAll(statuses);
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
            boolean isCreator = session.isLoggedIn()
                    && g.creatorUsername != null
                    && g.creatorUsername.equalsIgnoreCase(session.getUsername());

            String initial = (g.name != null && !g.name.isEmpty())
                    ? String.valueOf(g.name.charAt(0)).toUpperCase() : "G";
            h.tvAvatar.setText(initial);
            h.tvName.setText(g.name);
            String visibility = g.isPublic() ? "Public" : "Privé";
            String desc = g.description != null && !g.description.isEmpty()
                    ? g.description : "Aucune description";
            h.tvDesc.setText(visibility + " · " + desc);

            if (mode == MODE_MY) {
                bindMyGroup(h, g, isCreator);
            } else {
                bindDiscoverGroup(h, g, isCreator);
            }
        }

        private void bindMyGroup(GVH h, Group g, boolean isCreator) {
            h.tvAction.setVisibility(isCreator ? View.VISIBLE : View.GONE);
            if (isCreator) {
                h.tvAction.setText("👑 Admin");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.sand, null));
            }

            // Clicking the whole card opens the chat
            h.itemView.setOnClickListener(v -> {
                viewModel.markGroupMessagesRead(session.getUserId(), g.id);
                h.tvUnreadBadge.setVisibility(View.GONE);
                openChat(v, g);
            });

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
            h.tvAction.setVisibility(View.VISIBLE);
            
            if (isCreator) {
                h.tvAction.setText("👑 Mon groupe");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.sand, null));
                h.itemView.setOnClickListener(v -> openChat(v, g));
                h.tvAction.setOnClickListener(v -> openChat(v, g));
                return;
            }

            String status = memberships.get(g.name);

            if ("MEMBER".equals(status)) {
                h.tvAction.setText("Membre ✓");
                h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.comfort_green, null));
                h.itemView.setOnClickListener(v -> openChat(v, g));
                h.tvAction.setOnClickListener(v ->
                        new AlertDialog.Builder(v.getContext())
                                .setTitle("Quitter " + g.name + " ?")
                                .setPositiveButton("Quitter", (d, w) -> {
                                    FirebaseRepository.getInstance().deleteGroupMember(g.name, session.getUsername());
                                    memberships.remove(g.name);
                                    notifyDataSetChanged();
                                })
                                .setNegativeButton("Annuler", null)
                                .show());
            } else {
                h.itemView.setOnClickListener(null); // Can't open chat if not a member
                if ("PENDING".equals(status)) {
                    h.tvAction.setText("⏳ En attente");
                    h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.ink_faint, null));
                    h.tvAction.setOnClickListener(v ->
                            Toast.makeText(v.getContext(), "Demande envoyée, en attente d'approbation.", Toast.LENGTH_SHORT).show());
                } else if ("INVITED".equals(status)) {
                    h.tvAction.setText("Invitation");
                    h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.coral, null));
                    h.tvAction.setOnClickListener(v ->
                            new android.app.AlertDialog.Builder(v.getContext())
                                    .setTitle("Rejoindre " + g.name + " ?")
                                    .setMessage("Vous avez été invité à rejoindre ce groupe.")
                                    .setPositiveButton("Accepter", (d, w) -> {
                                        FirebaseRepository.getInstance().acceptGroupInvitation(g.name, session.getUsername());
                                        memberships.put(g.name, "MEMBER");
                                        notifyDataSetChanged();
                                    })
                                    .setNegativeButton("Refuser", (d, w) -> {
                                        FirebaseRepository.getInstance().declineGroupInvitation(g.name, session.getUsername());
                                        memberships.remove(g.name);
                                        notifyDataSetChanged();
                                    })
                                    .show());
                } else {
                    h.tvAction.setText(g.isPublic() ? "Rejoindre →" : "Demander →");
                    h.tvAction.setTextColor(h.tvAction.getContext().getResources().getColor(R.color.coral, null));
                    h.tvAction.setOnClickListener(v -> {
                        if (!session.isLoggedIn()) {
                            Toast.makeText(v.getContext(), "Connectez-vous pour rejoindre un groupe.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String newStatus = g.isPublic() ? "MEMBER" : "PENDING";
                        FirebaseRepository.getInstance().saveGroupMember(g.name, session.getUsername(), newStatus);
                        memberships.put(g.name, newStatus);
                        notifyDataSetChanged();
                    });
                }
            }
        }

        private void openChat(View v, Group g) {
            androidx.fragment.app.FragmentActivity activity =
                    (androidx.fragment.app.FragmentActivity) v.getContext();
            activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container,
                            GroupChatFragment.newInstance(g.id, g.name, g.creatorId, g.creatorUsername))
                    .addToBackStack(null)
                    .commit();
        }

        @Override public int getItemCount() { return groups.size(); }
    }
}
