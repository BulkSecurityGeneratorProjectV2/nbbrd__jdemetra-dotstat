/*
 * Copyright 2017 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package be.nbb.sdmx.facade.tck;

import be.nbb.sdmx.facade.LanguagePriorityList;
import be.nbb.sdmx.facade.SdmxConnection;
import be.nbb.sdmx.facade.SdmxConnectionSupplier;
import java.io.IOException;
import org.assertj.core.api.SoftAssertions;

/**
 *
 * @author Philippe Charles
 */
public final class ConnectionSupplierAssert {

    public static void assertCompliance(SdmxConnectionSupplier supplier, String validName, String invalidName) {
        SoftAssertions s = new SoftAssertions();
        try {
            assertCompliance(s, supplier, validName, invalidName);
        } catch (Exception ex) {
            s.fail("Unexpected exception '" + ex.getClass() + "'", ex);
        }
        s.assertAll();
    }

    public static void assertCompliance(SoftAssertions s, SdmxConnectionSupplier supplier, String name, String invalidName) throws Exception {
        assertNonnull(s, supplier, name);

        s.assertThatThrownBy(() -> supplier.getConnection(invalidName)).isInstanceOf(IOException.class);

        try (SdmxConnection conn = supplier.getConnection(name)) {
            s.assertThat(conn).isNotNull();
        }
    }

    @SuppressWarnings("null")
    private static void assertNonnull(SoftAssertions s, SdmxConnectionSupplier supplier, String name) {
        s.assertThatThrownBy(() -> supplier.getConnection(null))
                .as("Expecting 'getConnection(String, LanguagePriorityList)' to raise NPE when called with null name")
                .isInstanceOf(NullPointerException.class);

        s.assertThatThrownBy(() -> supplier.getConnection(null, LanguagePriorityList.ANY))
                .as("Expecting 'getConnection(String, LanguagePriorityList)' to raise NPE when called with null name")
                .isInstanceOf(NullPointerException.class);

        s.assertThatThrownBy(() -> supplier.getConnection(name, null))
                .as("Expecting 'getConnection(String, LanguagePriorityList)' to raise NPE when called with null language")
                .isInstanceOf(NullPointerException.class);
    }
}
