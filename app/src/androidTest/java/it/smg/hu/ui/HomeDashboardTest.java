package it.smg.hu.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import it.smg.hu.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class HomeDashboardTest {
    @Test
    public void dashboardShowsConnectionStatusWithoutPhone() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.statusTitle)).check(matches(isDisplayed()));
            onView(withId(R.id.statusBadge)).check(matches(isDisplayed()));
            onView(withId(R.id.settingsBtn)).check(matches(isDisplayed()));
        }
    }
}
