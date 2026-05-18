package com.example.travelshare;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.travelshare.ui.InscriptionFragment;
import com.example.travelshare.ui.theme.fragments.ExplorerFragment;
import com.example.travelshare.ui.theme.fragments.GroupsFragment;
import com.example.travelshare.ui.theme.fragments.TravelPathFragment;
import com.example.travelshare.ui.theme.fragments.MapFragment;
import com.example.travelshare.ui.theme.fragments.NotificationsFragment;
import com.example.travelshare.ui.theme.fragments.ProfileFragment;
import com.example.travelshare.ui.theme.fragments.PublishFragment;
import com.example.travelshare.data.AppDatabase;
import com.example.travelshare.data.repository.FirebaseRepository;
import com.example.travelshare.utils.SessionManager;
import com.example.travelshare.viewmodels.SharedViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity implements InscriptionFragment.OnInscriptionListener {

    private BottomNavigationView bottomNav;
    private ListenerRegistration notifListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SessionManager session = new SessionManager(this);
        String openFragment = getIntent().getStringExtra("OPEN_FRAGMENT");

        if (!session.hasActiveSession() && !"INSCRIPTION".equals(openFragment)) {
            Intent intent = new Intent(this, com.example.travelshare.ui.LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);

        org.osmdroid.config.Configuration.getInstance().setUserAgentValue(getPackageName());
        org.osmdroid.config.Configuration.getInstance().setOsmdroidTileCache(
                new java.io.File(getCacheDir(), "osmdroid_tiles"));

        boolean openTravelPath = getIntent().getBooleanExtra("OPEN_TRAVELPATH", false);
        String travelPathCity  = getIntent().getStringExtra("TRAVELPATH_CITY");

        if (savedInstanceState == null) {
            if ("INSCRIPTION".equals(openFragment)) {
                bottomNav.setVisibility(View.GONE);
                loadFragment(new InscriptionFragment());
            } else if (openTravelPath) {
                bottomNav.setVisibility(View.VISIBLE);
                bottomNav.setSelectedItemId(R.id.nav_map);
                String city = travelPathCity != null ? travelPathCity : "";
                loadFragment(TravelPathFragment.newInstance(city));
            } else {
                bottomNav.setVisibility(View.VISIBLE);
                loadFragment(new ExplorerFragment());
            }
        }

        View btnBellContainer = findViewById(R.id.btn_notif_bell);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            boolean isGuest = !new SessionManager(this).isLoggedIn();

            // Onglets réservés aux connectés
            if (isGuest && (itemId == R.id.nav_publish || itemId == R.id.nav_groups)) {
                Toast.makeText(this,
                        "Connectez-vous pour accéder à cette fonctionnalité",
                        Toast.LENGTH_LONG).show();
                return false;
            }

            Fragment selectedFragment = null;
            if (itemId == R.id.nav_explorer) {
                selectedFragment = new ExplorerFragment();
            } else if (itemId == R.id.nav_map) {
                selectedFragment = new MapFragment();
            } else if (itemId == R.id.nav_publish) {
                selectedFragment = new PublishFragment();
            } else if (itemId == R.id.nav_groups) {
                selectedFragment = new GroupsFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            boolean hideBell = itemId == R.id.nav_groups || itemId == R.id.nav_map;
            btnBellContainer.setVisibility(hideBell ? View.GONE : View.VISIBLE);

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });

        View badge = findViewById(R.id.notif_badge);
        SharedViewModel viewModel = new ViewModelProvider(this).get(SharedViewModel.class);
        long userId = session.getUserId();
        viewModel.getUnreadNotificationCount(userId).observe(this, count ->
                badge.setVisibility(count != null && count > 0 ? View.VISIBLE : View.GONE));
        startNotificationListener(session);

        btnBellContainer.setOnClickListener(v -> loadFragment(new NotificationsFragment()));
    }

    private void startNotificationListener(SessionManager session) {
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
        if (!session.isLoggedIn()) return;
        notifListener = FirebaseRepository.getInstance().listenToNotifications(
                session.getUsername(), AppDatabase.getInstance(this), session.getUserId());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        boolean openTravelPath = intent.getBooleanExtra("OPEN_TRAVELPATH", false);
        if (openTravelPath) {
            String city = intent.getStringExtra("TRAVELPATH_CITY");
            bottomNav.setVisibility(View.VISIBLE);
            loadFragment(TravelPathFragment.newInstance(city != null ? city : ""));
        }
        
        boolean openDirectChat = intent.getBooleanExtra("OPEN_DIRECT_CHAT", false);
        if (openDirectChat) {
            String target = intent.getStringExtra("DIRECT_CHAT_USER");
            if (target != null) {
                loadFragment(com.example.travelshare.ui.theme.fragments.DirectChatFragment.newInstance(target));
            }
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public void onInscriptionReussie(long newUserId, String username) {
        SessionManager session = new SessionManager(this);
        session.createLoginSession((int) newUserId, username);
        startNotificationListener(session);
        Toast.makeText(this, "Bienvenue " + username + " !", Toast.LENGTH_SHORT).show();
        bottomNav.setVisibility(View.VISIBLE);
        loadFragment(new ExplorerFragment());
    }
}
