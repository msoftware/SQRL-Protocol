/*
 * Copyright (C) 2014 Ralf Wondratschek
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
package net.vrallev.java.sqrl.test;

import net.vrallev.java.sqrl.Identities;
import net.vrallev.java.sqrl.SqrlException;
import net.vrallev.java.sqrl.SqrlProtocol;
import net.vrallev.java.sqrl.TestUtils;
import net.vrallev.java.sqrl.body.ServerParameter;
import net.vrallev.java.sqrl.body.SqrlClientBody;
import net.vrallev.java.sqrl.body.SqrlServerBody;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Ralf Wondratschek
 */
public class ChangeKeysTest {

    private String mSiteKey = "sqrl-login.appspot.com";

    @SuppressWarnings("FieldCanBeLocal")
    private String mSignatureUri = "sqrl-login.appspot.com:443/sqrl/auth?nut=5b216fa381b7769e1e88624ff685686c";

    @Test
    public void testSimpleAuthentication() {
        Identities[] identities = Identities.values();

        try {
            for (int i = 0; i < identities.length - 1; i++) {
                Identities identityOld = identities[i];
                Identities identityNew = identities[i + 1];

                byte[][] keys = TestUtils.createServerKeys(identityOld, SqrlProtocol.instance().getEccProvider());
                byte[] serverUnlockKeyOld = keys[0];
                byte[] verifyUnlockKeyOld = keys[1];

                keys = TestUtils.createServerKeys(identityNew, SqrlProtocol.instance().getEccProvider());
                byte[] serverUnlockKeyNew = keys[0];
                byte[] verifyUnlockKeyNew = keys[1];


                SqrlClientBody clientBody = authenticate(identityNew, identityOld);
                SqrlServerBody serverBody = oldKeysUsed(clientBody, serverUnlockKeyOld, verifyUnlockKeyOld);

                clientBody = updateKeys(serverBody, identityNew, identityOld, serverUnlockKeyNew, verifyUnlockKeyNew);
                performUpdateKeys(clientBody);
            }
        } catch (SqrlException e) {
            // should not happen
            assert false;
        }
    }

    private SqrlClientBody authenticate(Identities identityNew, Identities identityOld) throws SqrlException {
        SqrlClientBody bodyOriginal = SqrlProtocol.instance()
                .authenticate(identityNew.getMasterKey(), mSiteKey)
                .withPreviousMasterKey(identityOld.getMasterKey())
                .buildRequest(mSignatureUri);

        SqrlClientBody bodyParsed = SqrlProtocol.instance()
                .readSqrlClientBody()
                .from(bodyOriginal.getBodyEncoded())
                .verified();

        assertThat(bodyParsed.getClientParameter()).isNotNull();
        assertThat(bodyParsed.getServerParameter()).isNotNull();
        assertThat(bodyParsed.getServerParameter().isUri()).isTrue();

        assertThat(bodyParsed.getIdentitySignatureDecoded()).isNotNull().hasSize(64);
        assertThat(bodyParsed.getPreviousIdentitySignatureDecoded()).isNotNull().hasSize(64);

        assertThat(bodyParsed.getClientParameter().getServerUnlockKeyDecoded()).isNull();
        assertThat(bodyParsed.getClientParameter().getVerifyUnlockKeyDecoded()).isNull();

        assertThat(bodyOriginal).isEqualTo(bodyParsed);

        return bodyOriginal;
    }

    private SqrlServerBody oldKeysUsed(SqrlClientBody body, byte[] serverUnlockKey, byte[] verifyUnlockKey) throws SqrlException {
        SqrlServerBody bodyOriginal = SqrlProtocol.instance()
                .answerClient(body, ServerParameter.PREVIOUS_ID_MATCH)
                .withServerFriendlyName("Unit Test")
                .withStoredKeys(serverUnlockKey, verifyUnlockKey)
                .create()
                .asSqrlServerBody();

        SqrlServerBody bodyParsed = SqrlProtocol.instance()
                .readSqrlServerBody()
                .from(bodyOriginal.getBodyEncoded())
                .parsed();

        assertThat(bodyParsed.getServerParameter()).isNotNull();
        assertThat(bodyParsed.getServerParameter().getServerFriendlyNameDecoded()).isEqualTo("Unit Test");
        assertThat(bodyParsed.getServerParameter().getNutDecoded()).isEqualTo(body.getServerParameter().getNutDecoded()).isEqualTo("5b216fa381b7769e1e88624ff685686c");
        assertThat(bodyParsed.getServerParameter().getServerUnlockKeyDecoded()).isNotNull().hasSize(32);
        assertThat(bodyParsed.getServerParameter().getVerifyUnlockKeyDecoded()).isNotNull().hasSize(32);

        assertThat(bodyOriginal).isEqualTo(bodyParsed);

        return bodyOriginal;
    }

    private SqrlClientBody updateKeys(SqrlServerBody serverBody, Identities identityNew, Identities identityOld, byte[] sukNew, byte[] vukNew) throws SqrlException {
        SqrlClientBody bodyOriginal = SqrlProtocol.instance()
                .answerServer(identityNew.getMasterKey(), mSiteKey, serverBody)
                .addCommand("setkey")
                .withPreviousMasterKey(identityOld.getMasterKey())
                .withNewServerKeys(sukNew, vukNew)
                .withIdentityUnlockKey(identityOld.getIdentityUnlockKey())
                .buildResponse(serverBody);

        SqrlClientBody bodyParsed = SqrlProtocol.instance()
                .readSqrlClientBody()
                .from(bodyOriginal.getBodyEncoded())
                .withStoredKeys(serverBody.getServerParameter().getServerUnlockKeyDecoded(), serverBody.getServerParameter().getVerifyUnlockKeyDecoded())
                .verified();

        assertThat(bodyParsed.getClientParameter()).isNotNull();
        assertThat(bodyParsed.getServerParameter()).isNotNull();
        assertThat(bodyParsed.getIdentitySignatureDecoded()).isNotNull().hasSize(64);
        assertThat(bodyParsed.getPreviousIdentitySignatureDecoded()).isNotNull().hasSize(64);
        assertThat(bodyParsed.getUnlockRequestSignatureDecoded()).isNotNull().hasSize(64);

        assertThat(bodyParsed.getServerParameter().isUri()).isFalse();
        assertThat(bodyParsed.getServerParameter().getNutDecoded()).isEqualTo(serverBody.getServerParameter().getNutDecoded()).isNotNull();

        assertThat(bodyParsed.getClientParameter().getServerUnlockKeyDecoded()).isNotNull().hasSize(32).isEqualTo(sukNew);
        assertThat(bodyParsed.getClientParameter().getVerifyUnlockKeyDecoded()).isNotNull().hasSize(32).isEqualTo(vukNew);

        assertThat(bodyOriginal).isEqualTo(bodyParsed);

        return bodyOriginal;
    }

    private SqrlServerBody performUpdateKeys(SqrlClientBody clientBody) throws SqrlException {
        SqrlServerBody bodyOriginal = SqrlProtocol.instance()
                .answerClient(clientBody, ServerParameter.ID_MATCH)
                .withServerFriendlyName("Unit Test")
                .withStoredKeys(clientBody.getClientParameter().getServerUnlockKeyDecoded(), clientBody.getClientParameter().getVerifyUnlockKeyDecoded())
                .create()
                .asSqrlServerBody();

        SqrlServerBody bodyParsed = SqrlProtocol.instance()
                .readSqrlServerBody()
                .from(bodyOriginal.getBodyEncoded())
                .parsed();

        assertThat(bodyParsed.getServerParameter()).isNotNull();
        assertThat(bodyParsed.getServerParameter().getServerFriendlyNameDecoded()).isEqualTo("Unit Test");
        assertThat(bodyParsed.getServerParameter().getNutDecoded()).isEqualTo(clientBody.getServerParameter().getNutDecoded()).isEqualTo("5b216fa381b7769e1e88624ff685686c");
        assertThat(bodyParsed.getServerParameter().getServerUnlockKeyDecoded()).isNotNull().hasSize(32);
        assertThat(bodyParsed.getServerParameter().getVerifyUnlockKeyDecoded()).isNotNull().hasSize(32);

        assertThat(bodyOriginal).isEqualTo(bodyParsed);

        return bodyOriginal;
    }
}
