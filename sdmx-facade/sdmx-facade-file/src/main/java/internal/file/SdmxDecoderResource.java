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
package internal.file;

import be.nbb.sdmx.facade.DataCursor;
import be.nbb.sdmx.facade.DataStructure;
import be.nbb.sdmx.facade.DataflowRef;
import be.nbb.sdmx.facade.Key;
import be.nbb.sdmx.facade.LanguagePriorityList;
import be.nbb.sdmx.facade.file.SdmxFileSet;
import be.nbb.sdmx.facade.parser.DataFactory;
import be.nbb.sdmx.facade.xml.stream.SdmxXmlStreams;
import be.nbb.util.Stax;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;

/**
 *
 * @author Philippe Charles
 */
@lombok.AllArgsConstructor
class SdmxDecoderResource implements SdmxFileConnectionImpl.Resource {

    private final SdmxFileSet files;
    private final LanguagePriorityList languages;
    private final XMLInputFactory factoryWithoutNamespace;
    private final SdmxDecoder decoder;
    private final Optional<DataFactory> dataFactory;

    @Override
    public SdmxDecoder.Info decode() throws IOException {
        return decoder.decode(files, languages);
    }

    @Override
    public DataCursor loadData(SdmxDecoder.Info entry, DataflowRef flowRef, Key key, boolean serieskeysonly) throws IOException {
        return getDataSupplier(entry.getType(), entry.getStructure())
                .onFile(factoryWithoutNamespace, StandardCharsets.UTF_8)
                .parseWithIO(files.getData());
    }

    private Stax.Parser<DataCursor> getDataSupplier(SdmxDecoder.DataType o, DataStructure dsd) throws IOException {
        switch (o) {
            case GENERIC20:
                return SdmxXmlStreams.genericData20(dsd, dataFactory.orElse(DataFactory.sdmx20()));
            case COMPACT20:
                return SdmxXmlStreams.compactData20(dsd, dataFactory.orElse(DataFactory.sdmx20()));
            case GENERIC21:
                return SdmxXmlStreams.genericData21(dsd, dataFactory.orElse(DataFactory.sdmx21()));
            case COMPACT21:
                return SdmxXmlStreams.compactData21(dsd, dataFactory.orElse(DataFactory.sdmx21()));
            default:
                throw new IOException("Don't known how to handle type '" + o + "'");
        }
    }
}
