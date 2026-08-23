package it.smg.hu.ui;

import android.preference.PreferenceManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import it.smg.hu.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class HomeDashboardTest {
    @Test
    public void dashboardShowsConnectionStatusWithoutPhone() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.statusTitle)).check(matches(isDisplayed()));
            onView(withId(R.id.statusBadge)).check(matches(isDisplayed()));
            onView(withId(R.id.settingsBtn)).check(matches(isDisplayed()));
            onView(withId(R.id.themeBtn)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void themeStartsLightAndPersistsTheUserChoice() {
        PreferenceManager.getDefaultSharedPreferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit().remove("darkTheme").commit();

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.themeBtn)).check(matches(withText(R.string.theme_light)));
            onView(withId(R.id.themeBtn)).perform(click());
            onView(withId(R.id.themeBtn)).check(matches(withText(R.string.theme_dark)));
        }

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.themeBtn)).check(matches(withText(R.string.theme_dark)));
        } finally {
            PreferenceManager.getDefaultSharedPreferences(
                    InstrumentationRegistry.getInstrumentation().getTargetContext())
                    .edit().remove("darkTheme").commit();
        }
    }

    @Test
    public void settingsKeepsTheAutomotiveShellAndThemeSelectorVisible() {
        PreferenceManager.getDefaultSharedPreferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext())
                .edit().remove("darkTheme").commit();
        try (ActivityScenario<SettingsActivity> ignored = ActivityScenario.launch(SettingsActivity.class)) {
            onView(withId(R.id.settingsTitle)).check(matches(isDisplayed()));
            onView(withId(R.id.settingsThemeBtn)).check(matches(isDisplayed()));
            onView(withId(R.id.settingsNav)).check(matches(isDisplayed()));
            onView(withId(R.id.settingsThemeBtn)).perform(click());
            onView(withId(R.id.settingsThemeBtn)).check(matches(withText(R.string.theme_dark)));
            onView(withId(R.id.main_content)).check(matches(isDisplayed()));
        } finally {
            PreferenceManager.getDefaultSharedPreferences(
                    InstrumentationRegistry.getInstrumentation().getTargetContext())
                    .edit().remove("darkTheme").commit();
        }
    }
}
