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
package internal.sdmx;

import be.nbb.demetra.sdmx.HasSdmxProperties;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.AccessLevel;
import sdmxdl.LanguagePriorityList;
import sdmxdl.SdmxManager;

/**
 *
 * @author Philippe Charles
 * @param <M>
 */
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SdmxPropertiesSupport<M extends SdmxManager> implements HasSdmxProperties<M> {

    public static <M extends SdmxManager> HasSdmxProperties<M> of(Supplier<M> defaultManager, Runnable onManagerChange) {
        return new SdmxPropertiesSupport<>(
                defaultManager,
                new AtomicReference<>(defaultManager.get()),
                onManagerChange);
    }

    private final Supplier<M> defaultManager;
    private final AtomicReference<M> manager;
    private final Runnable onManagerChange;

    @Override
    public M getSdmxManager() {
        return manager.get();
    }

    @Override
    public void setSdmxManager(M manager) {
        M old = this.manager.get();
        if (this.manager.compareAndSet(old, manager != null ? manager : defaultManager.get())) {
            onManagerChange.run();
        }
    }

    public static Optional<LanguagePriorityList> tryParseLangs(String text) {
        try {
            return Optional.of(LanguagePriorityList.parse(text));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
