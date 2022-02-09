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

import sdmxdl.DataStructure;
import sdmxdl.DataflowRef;
import sdmxdl.Key;
import sdmxdl.SdmxConnection;
import ec.tss.tsproviders.cube.CubeAccessor;
import ec.tss.tsproviders.cube.CubeId;
import ec.tss.tsproviders.cursor.TsCursor;
import ec.tss.tsproviders.utils.IteratorWithIO;
import ec.tstoolkit.design.VisibleForTesting;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import nbbrd.io.function.IOSupplier;
import sdmxdl.util.SdmxCubeUtil;

/**
 *
 * @author Philippe Charles
 */
@lombok.AllArgsConstructor(staticName = "of")
public final class SdmxCubeAccessor implements CubeAccessor {

    private final IOSupplier<SdmxConnection> supplier;
    private final DataflowRef flowRef;
    private final CubeId root;
    private final String labelAttribute;
    private final String sourceLabel;
    private final boolean displayCodes;

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
        SdmxConnection conn = supplier.getWithIO();
        try {
            return getAllSeries(conn, flowRef, ref, labelAttribute).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(close(conn, ex));
        }
    }

    @Override
    public TsCursor<CubeId> getAllSeriesWithData(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getWithIO();
        try {
            return getAllSeriesWithData(conn, flowRef, ref, labelAttribute).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(close(conn, ex));
        }
    }

    @Override
    public TsCursor<CubeId> getSeriesWithData(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getWithIO();
        try {
            return getSeriesWithData(conn, flowRef, ref, labelAttribute).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(close(conn, ex));
        }
    }

    @Override
    public IteratorWithIO<CubeId> getChildren(CubeId ref) throws IOException {
        SdmxConnection conn = supplier.getWithIO();
        try {
            return getChildren(conn, flowRef, ref).onClose(conn);
        } catch (IOException ex) {
            throw close(conn, ex);
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(close(conn, ex));
        }
    }

    @Override
    public String getDisplayName() throws IOException {
        try (SdmxConnection conn = supplier.getWithIO()) {
            return String.format("%s ~ %s", sourceLabel, conn.getFlow(flowRef).getLabel());
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(ex);
        }
    }

    @Override
    public String getDisplayName(CubeId id) throws IOException {
        if (id.isVoid()) {
            return "All";
        }
        try (SdmxConnection conn = supplier.getWithIO()) {
            return getKey(conn.getStructure(flowRef), id).toString();
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(ex);
        }
    }

    @Override
    public String getDisplayNodeName(CubeId id) throws IOException {
        if (id.isVoid()) {
            return "All";
        }
        try (SdmxConnection conn = supplier.getWithIO()) {
            return displayCodes
                    ? getDimensionCodeId(id)
                    : getDimensionCodeLabel(id, conn.getStructure(flowRef));
        } catch (RuntimeException ex) {
            throw new UnexpectedIOException(ex);
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Implementation details">
    private static TsCursor<CubeId> getAllSeries(SdmxConnection conn, DataflowRef flow, CubeId node, String labelAttribute) throws IOException {
        KeyConverter converter = KeyConverter.of(conn.getStructure(flow), node);
        return SdmxQueryUtil
                .getAllSeries(conn, flow, converter.toKey(node), labelAttribute)
                .transform(converter::fromKey);
    }

    private static TsCursor<CubeId> getAllSeriesWithData(SdmxConnection conn, DataflowRef flow, CubeId node, String labelAttribute) throws IOException {
        KeyConverter converter = KeyConverter.of(conn.getStructure(flow), node);
        return SdmxQueryUtil
                .getAllSeriesWithData(conn, flow, converter.toKey(node), labelAttribute)
                .transform(converter::fromKey);
    }

    private static TsCursor<CubeId> getSeriesWithData(SdmxConnection conn, DataflowRef flow, CubeId leaf, String labelAttribute) throws IOException {
        KeyConverter converter = KeyConverter.of(conn.getStructure(flow), leaf);
        return SdmxQueryUtil
                .getSeriesWithData(conn, flow, converter.toKey(leaf), labelAttribute)
                .transform(converter::fromKey);
    }

    private static IteratorWithIO<CubeId> getChildren(SdmxConnection conn, DataflowRef flow, CubeId node) throws IOException {
        DataStructure dsd = conn.getStructure(flow);
        KeyConverter converter = KeyConverter.of(dsd, node);
        String dimensionId = node.getDimensionId(node.getLevel());
        int dimensionIndex = SdmxCubeUtil.getDimensionIndexById(dsd, dimensionId).orElseThrow(RuntimeException::new);
        List<String> children = SdmxQueryUtil.getChildren(conn, flow, converter.toKey(node), dimensionIndex);
        return IteratorWithIO.from(children.iterator()).transform(node::child);
    }

    private static <EX extends Throwable> EX close(SdmxConnection conn, EX ex) {
        try {
            conn.close();
        } catch (IOException other) {
            ex.addSuppressed(other);
        }
        return ex;
    }

    private static String getDimensionCodeId(CubeId ref) {
        int index = ref.getLevel() - 1;
        String codeId = ref.getDimensionValue(index);
        return codeId;
    }

    private static String getDimensionCodeLabel(CubeId ref, DataStructure dsd) {
        if (ref.isRoot()) {
            return "Invalid reference '" + dump(ref) + "'";
        }
        int index = ref.getLevel() - 1;
        Map<String, String> codes = SdmxCubeUtil.getDimensionById(dsd, ref.getDimensionId(index)).orElseThrow(RuntimeException::new).getCodes();
        String codeId = ref.getDimensionValue(index);
        return codes.getOrDefault(codeId, codeId);
    }

    private static String dump(CubeId ref) {
        return IntStream.range(0, ref.getMaxLevel())
                .mapToObj(o -> ref.getDimensionId(o) + "=" + (o < ref.getLevel() ? ref.getDimensionValue(0) : "null"))
                .collect(Collectors.joining(", "));
    }

    @VisibleForTesting
    static Key getKey(DataStructure dsd, CubeId ref) {
        if (ref.isRoot()) {
            return Key.ALL;
        }
        String[] result = new String[ref.getMaxLevel()];
        for (int i = 0; i < result.length; i++) {
            String id = ref.getDimensionId(i);
            String value = i < ref.getLevel() ? ref.getDimensionValue(i) : "";
            int index = SdmxCubeUtil.getDimensionIndexById(dsd, id).orElseThrow(RuntimeException::new);
            result[index] = value;
        }
        return Key.of(result);
    }

    @lombok.RequiredArgsConstructor
    static final class KeyConverter {

        static KeyConverter of(DataStructure dsd, CubeId ref) {
            return new KeyConverter(dsd, new CubeIdBuilder(dsd, ref));
        }

        final DataStructure dsd;
        final CubeIdBuilder builder;

        public Key toKey(CubeId a) {
            return getKey(dsd, a);
        }

        public CubeId fromKey(Key b) {
            return builder.getId(b);
        }
    }

    private static final class CubeIdBuilder {

        private final CubeId ref;
        private final int[] indices;
        private final String[] dimValues;

        CubeIdBuilder(DataStructure dsd, CubeId ref) {
            this.ref = ref;
            this.indices = new int[ref.getDepth()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = SdmxCubeUtil.getDimensionIndexById(dsd, ref.getDimensionId(ref.getLevel() + i)).orElseThrow(RuntimeException::new);
            }
            this.dimValues = new String[indices.length];
        }

        public CubeId getId(Key o) {
            for (int i = 0; i < indices.length; i++) {
                dimValues[i] = o.get(indices[i]);
            }
            return ref.child(dimValues);
        }
    }
    //</editor-fold>
}
