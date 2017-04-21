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
package internal.sdmx;

import be.nbb.sdmx.facade.DataStructure;
import be.nbb.sdmx.facade.DataflowRef;
import be.nbb.sdmx.facade.Dimension;
import be.nbb.sdmx.facade.Key;
import be.nbb.sdmx.facade.SdmxConnection;
import be.nbb.sdmx.facade.SdmxConnectionSupplier;
import com.google.common.base.Converter;
import com.google.common.collect.Maps;
import ec.tss.tsproviders.cube.CubeAccessor;
import ec.tss.tsproviders.cube.CubeId;
import ec.tss.tsproviders.cursor.TsCursor;
import ec.tss.tsproviders.utils.IteratorWithIO;
import ec.tstoolkit.design.VisibleForTesting;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Philippe Charles
 */
public final class SdmxCubeAccessor implements CubeAccessor {

    public static SdmxCubeAccessor create(SdmxConnectionSupplier supplier, String source, DataflowRef flow, List<String> dimensions, String labelAttribute) {
        return new SdmxCubeAccessor(supplier, source, flow, CubeId.root(dimensions), labelAttribute);
    }

    private final SdmxConnectionSupplier supplier;
    private final String source;
    private final DataflowRef flowRef;
    private final CubeId root;
    private final String labelAttribute;

    private SdmxCubeAccessor(SdmxConnectionSupplier supplier, String source, DataflowRef flowRef, CubeId root, String labelAttribute) {
        this.supplier = supplier;
        this.source = source;
        this.flowRef = flowRef;
        this.root = root;
        this.labelAttribute = labelAttribute;
    }

    @Override
    public IOException testConnection() {
        return null;
    }

    @Override
    public CubeId getRoot() {
        return root;
    }

    @Override
    public TsCursor<CubeId> getAllSeries(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getConnection(source);
        try {
            return getAllSeriesCursor(conn, flowRef, ref, labelAttribute).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        }
    }

    @Override
    public TsCursor<CubeId> getAllSeriesWithData(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getConnection(source);
        try {
            return getAllSeriesWithDataCursor(conn, flowRef, ref, labelAttribute).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        }
    }

    @Override
    public TsCursor<CubeId> getSeriesWithData(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getConnection(source);
        try {
            return getSeriesWithDataCursor(conn, flowRef, ref).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        }
    }

    @Override
    public IteratorWithIO<CubeId> getChildren(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getConnection(source);
        try {
            return getChildren(conn, flowRef, ref).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        }
    }

    @Override
    public String getDisplayName() throws IOException {
        try (SdmxConnection conn = supplier.getConnection(source)) {
            return String.format("%s ~ %s", source, conn.getDataflow(flowRef).getLabel());
        }
    }

    @Override
    public String getDisplayName(CubeId id) throws IOException {
        if (id.isVoid()) {
            return "All";
        }
        try (SdmxConnection conn = supplier.getConnection(source)) {
            Map<String, Dimension> dimensionById = dimensionById(conn.getDataStructure(flowRef));
            return getKey(dimensionById, id).toString();
        }
    }

    @Override
    public String getDisplayNodeName(CubeId id) throws IOException {
        if (id.isVoid()) {
            return "All";
        }
        try (SdmxConnection conn = supplier.getConnection(source)) {
            Map<String, Dimension> dimensionById = dimensionById(conn.getDataStructure(flowRef));
            return getDisplayName(dimensionById, id, id.getLevel() - 1);
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Implementation details">
    private static TsCursor<CubeId> getAllSeriesCursor(SdmxConnection conn, DataflowRef flowRef, CubeId ref, String labelAttribute) throws IOException {
        Converter<CubeId, Key> converter = getConverter(conn.getDataStructure(flowRef), ref);

        Key colKey = converter.convert(ref);
        TsCursor<Key> cursor = SdmxQueryUtil.getAllSeries(conn, flowRef, colKey, labelAttribute);

        return cursor.transform(converter.reverse()::convert);
    }

    private static TsCursor<CubeId> getAllSeriesWithDataCursor(SdmxConnection conn, DataflowRef flowRef, CubeId ref, String labelAttribute) throws IOException {
        Converter<CubeId, Key> converter = getConverter(conn.getDataStructure(flowRef), ref);

        Key colKey = converter.convert(ref);
        TsCursor<Key> cursor = SdmxQueryUtil.getAllSeriesWithData(conn, flowRef, colKey, labelAttribute);

        return cursor.transform(converter.reverse()::convert);
    }

    private static TsCursor<CubeId> getSeriesWithDataCursor(SdmxConnection conn, DataflowRef flowRef, CubeId ref) throws IOException {
        Converter<CubeId, Key> converter = getConverter(conn.getDataStructure(flowRef), ref);

        Key seriesKey = converter.convert(ref);
        TsCursor<Key> cursor = TsCursor.singleton(seriesKey, SdmxQueryUtil.getSeriesWithData(conn, flowRef, seriesKey));

        return cursor.transform(converter.reverse()::convert);
    }

    private static IteratorWithIO<CubeId> getChildren(SdmxConnection conn, DataflowRef flowRef, CubeId ref) throws IOException {
        Converter<CubeId, Key> converter = getConverter(conn.getDataStructure(flowRef), ref);
        int dimensionPosition = dimensionById(conn.getDataStructure(flowRef)).get(ref.getDimensionId(ref.getLevel())).getPosition();
        List<String> children = SdmxQueryUtil.getChildren(conn, flowRef, converter.convert(ref), dimensionPosition);
        return IteratorWithIO.from(children.iterator()).transform(ref::child);
    }

    private static IOException close(SdmxConnection conn, IOException ex) {
        try {
            conn.close();
        } catch (IOException other) {
            ex.addSuppressed(other);
        }
        return ex;
    }

    private static String getDisplayName(Map<String, Dimension> dimensionById, CubeId id, int index) {
        return dimensionById.get(id.getDimensionId(index)).getCodes().get(id.getDimensionValue(index));
    }

    @VisibleForTesting
    static Key getKey(Map<String, Dimension> dimensionById, CubeId ref) {
        if (ref.isRoot()) {
            return Key.ALL;
        }
        String[] result = new String[ref.getMaxLevel()];
        for (int i = 0; i < result.length; i++) {
            result[dimensionById.get(ref.getDimensionId(i)).getPosition() - 1] = i < ref.getLevel() ? ref.getDimensionValue(i) : "";
        }
        return Key.of(result);
    }

    static Converter<CubeId, Key> getConverter(DataStructure ds, CubeId ref) {
        final Map<String, Dimension> dimensionById = dimensionById(ds);
        final CubeIdBuilder builder = new CubeIdBuilder(dimensionById, ref);
        return new Converter<CubeId, Key>() {
            @Override
            protected Key doForward(CubeId a) {
                return getKey(dimensionById, a);
            }

            @Override
            protected CubeId doBackward(Key b) {
                return builder.getId(b);
            }
        };
    }

    private static final class CubeIdBuilder {

        private final CubeId ref;
        private final int[] indices;
        private final String[] dimValues;

        CubeIdBuilder(Map<String, Dimension> dimensionById, CubeId ref) {
            this.ref = ref;
            this.indices = new int[ref.getDepth()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = dimensionById.get(ref.getDimensionId(ref.getLevel() + i)).getPosition() - 1;
            }
            this.dimValues = new String[indices.length];
        }

        public CubeId getId(Key o) {
            for (int i = 0; i < indices.length; i++) {
                dimValues[i] = o.getItem(indices[i]);
            }
            return ref.child(dimValues);
        }
    }

    @VisibleForTesting
    static Map<String, Dimension> dimensionById(DataStructure ds) {
        return Maps.uniqueIndex(ds.getDimensions(), Dimension::getId);
    }
    //</editor-fold>
}
