/*
 * Copyright 2015 National Bank of Belgium
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
package be.nbb.demetra.dotstat;

import be.nbb.demetra.sdmx.HasSdmxProperties;
import sdmxdl.DataStructure;
import sdmxdl.Dimension;
import sdmxdl.Key;
import sdmxdl.Connection;
import sdmxdl.web.SdmxWebManager;
import com.google.common.collect.Maps;
import ec.tss.ITsProvider;
import ec.tss.TsAsyncMode;
import ec.tss.tsproviders.DataSet;
import ec.tss.tsproviders.DataSource;
import ec.tss.tsproviders.db.DbAccessor;
import ec.tss.tsproviders.db.DbBean;
import ec.tss.tsproviders.db.DbProvider;
import internal.sdmx.SdmxPropertiesSupport;
import java.io.IOException;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.openide.util.lookup.ServiceProvider;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Philippe Charles
 */
@Deprecated
@ServiceProvider(service = ITsProvider.class)
public final class DotStatProvider extends DbProvider<DotStatBean> implements HasSdmxProperties<SdmxWebManager> {

    public static final String NAME = "DOTSTAT", VERSION = "20150203";

    @lombok.experimental.Delegate
    private final HasSdmxProperties<SdmxWebManager> properties;

    private boolean displayCodes;

    public DotStatProvider() {
        super(LoggerFactory.getLogger(DotStatProvider.class), NAME, TsAsyncMode.Once);
        this.properties = SdmxPropertiesSupport.of(SdmxWebManager::ofServiceLoader, this::clearCache);
        this.displayCodes = false;
    }

    @Override
    protected DbAccessor<DotStatBean> loadFromBean(DotStatBean bean) throws Exception {
        return new DotStatAccessor(bean, getSdmxManager()).memoize();
    }

    @Override
    public DotStatBean newBean() {
        return new DotStatBean();
    }

    @Override
    public DotStatBean decodeBean(DataSource dataSource) throws IllegalArgumentException {
        return new DotStatBean(dataSource);
    }

    @Override
    public String getDisplayName() {
        return "SDMX Web Services";
    }

    @Override
    public String getDisplayName(DataSource dataSource) {
        DotStatBean bean = decodeBean(dataSource);
        if (!displayCodes) {
            try (Connection conn = connect(bean.getDbName())) {
                return String.format("%s ~ %s", bean.getDbName(), conn.getFlow(bean.getFlowRef()).getLabel());
            } catch (IOException | RuntimeException ex) {
            }
        }
        return bean.getTableName();
    }

    @Override
    public String getDisplayName(DataSet dataSet) {
        DotStatBean bean = decodeBean(dataSet.getDataSource());
        try (Connection conn = connect(bean.getDbName())) {
            DataStructure dfs = conn.getStructure(bean.getFlowRef());
            Key.Builder b = Key.builder(dfs);
            for (Dimension o : dfs.getDimensions()) {
                String value = dataSet.get(o.getId());
                if (value != null) {
                    b.put(o.getId(), value);
                }
            }
            return b.toString();
        } catch (IOException | RuntimeException ex) {
        }
        return super.getDisplayName(dataSet);
    }

    @Override
    public String getDisplayNodeName(DataSet dataSet) {
        Map.Entry<String, String> nodeDim = getNodeDimension(dataSet);
        if (nodeDim != null) {
            if (!displayCodes) {
                DotStatBean bean = decodeBean(dataSet.getDataSource());
                try (Connection conn = connect(bean.getDbName())) {
                    DataStructure dfs = conn.getStructure(bean.getFlowRef());
                    for (Dimension o : dfs.getDimensions()) {
                        if (o.getId().equals(nodeDim.getKey())) {
                            return o.getCodes().get(nodeDim.getValue());
                        }
                    }
                    return nodeDim.getValue();
                } catch (IOException | RuntimeException ex) {
                }
            }
            return nodeDim.getValue();
        }
        return "All";
    }

    @Override
    public DataSource encodeBean(Object bean) throws IllegalArgumentException {
        return support.checkBean(bean, DotStatBean.class).toDataSource(NAME, VERSION);
    }

    @NonNull
    public String getPreferredLanguage() {
        return getSdmxManager().getLanguages().toString();
    }

    public void setPreferredLanguage(@Nullable String lang) {
        SdmxWebManager manager = getSdmxManager();
        if (lang != null) {
            SdmxPropertiesSupport.tryParseLangs(lang)
                    .ifPresent(newLang -> setSdmxManager(manager.toBuilder().languages(newLang).build()));
        }
    }

    public boolean isDisplayCodes() {
        return displayCodes;
    }

    public void setDisplayCodes(boolean displayCodes) {
        this.displayCodes = displayCodes;
    }

    private Connection connect(String name) throws IOException {
        return getSdmxManager().getConnection(name);
    }

    private static Map.@Nullable Entry<String, String> getNodeDimension(DataSet dataSet) {
        String[] dimColumns = DbBean.getDimArray(dataSet.getDataSource());
        int length = dimColumns.length;
        while (length > 0 && dataSet.get(dimColumns[length - 1]) == null) {
            length--;
        }
        String[] dimValues = new String[length];
        for (int i = 0; i < length; i++) {
            dimValues[i] = dataSet.get(dimColumns[i]);
        }
        return length > 0
                ? Maps.immutableEntry(dimColumns[length - 1], dimValues[length - 1])
                : null;
    }
}
