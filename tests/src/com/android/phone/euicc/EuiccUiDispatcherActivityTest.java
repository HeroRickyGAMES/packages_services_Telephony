/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.phone.euicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.service.euicc.EuiccService;
import android.telephony.TelephonyManager;
import android.telephony.euicc.EuiccManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class EuiccUiDispatcherActivityTest {
    private static final Intent MANAGE_INTENT =
            new Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS);
    private static final Intent PROVISION_INTENT =
            new Intent(EuiccManager.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION);

    private static final ActivityInfo ACTIVITY_INFO = new ActivityInfo();
    static {
        ACTIVITY_INFO.packageName = "test.package";
        ACTIVITY_INFO.name = "TestClass";
    }

    @Mock private Context mMockContext;
    @Mock private EuiccManager mMockEuiccManager;
    @Mock private TelephonyManager mTelephonyManager;
    private ActivityInfo mActivityInfo = ACTIVITY_INFO;
    private Intent mIntent = MANAGE_INTENT;
    private EuiccUiDispatcherActivity mActivity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockEuiccManager.isEnabled()).thenReturn(true);
        when(mMockContext.getSystemService(Context.EUICC_SERVICE)).thenReturn(mMockEuiccManager);
        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mTelephonyManager);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        mActivity = new TestEuiccUiDispatcherActivity();
                    }
                }
        );
    }

    @Test
    public void testResolveEuiccUiIntent_disabled() {
        when(mMockEuiccManager.isEnabled()).thenReturn(false);
        assertNull(mActivity.resolveEuiccUiIntent());
    }

    @Test
    public void testResolveEuiccUiIntent_nullAction() {
        mIntent = new Intent();
        assertNull(mActivity.resolveEuiccUiIntent());
    }

    @Test
    public void testResolveEuiccUiIntent_unsupportedAction() {
        mIntent = new Intent("fake.action");
        assertNull(mActivity.resolveEuiccUiIntent());
    }

    @Test
    public void testResolveEuiccUiIntent_noImplementation() {
        mActivityInfo = null;
        assertNull(mActivity.resolveEuiccUiIntent());
    }

    @Test
    public void testResolveEuiccUiIntent_validManage() {
        assertNotNull(mActivity.resolveEuiccUiIntent());
    }

    @Test
    public void testResolveEuiccUiIntent_validProvision() {
        assertNotNull(mActivity.resolveEuiccUiIntent());
    }

    @Test
    public void testExtrasPropagated() {
        mIntent.putExtra("foo", "bar");

        Intent euiccUiIntent = mActivity.resolveEuiccUiIntent();
        assertNotNull(euiccUiIntent);
        assertEquals("bar", euiccUiIntent.getStringExtra("foo"));
    }

    @Test
    public void testTransferEmbeddedSubscriptionsAction() {
        mIntent = new Intent(EuiccManager.ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS);
        Intent euiccUiIntent = mActivity.resolveEuiccUiIntent();
        assertNotNull(euiccUiIntent);
        assertEquals(EuiccService.ACTION_TRANSFER_EMBEDDED_SUBSCRIPTIONS,
                euiccUiIntent.getAction());
    }

    @Test
    public void testConvertToEmbeddedSubscriptionAction() {
        mIntent = new Intent(EuiccManager.ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTION);
        Intent euiccUiIntent = mActivity.resolveEuiccUiIntent();
        assertNotNull(euiccUiIntent);
        assertEquals(EuiccService.ACTION_CONVERT_TO_EMBEDDED_SUBSCRIPTION,
                euiccUiIntent.getAction());
    }

    class TestEuiccUiDispatcherActivity extends EuiccUiDispatcherActivity {
        public TestEuiccUiDispatcherActivity() {
            attachBaseContext(mMockContext);
        }

        @Override
        public Intent getIntent() {
            return mIntent;
        }

        @Override
        ActivityInfo findBestActivity(Intent euiccUiIntent) {
            return mActivityInfo;
        }

        @Override
        protected void grantDefaultPermissionsToLuiApp(ActivityInfo activityInfo) {}

        @Override
        protected void revokePermissionFromLuiApps(Intent intent) {}
    }
}
