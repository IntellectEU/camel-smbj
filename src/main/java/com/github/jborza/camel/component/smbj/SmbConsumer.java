/**
 *  Copyright [2018] [Juraj Borza]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.jborza.camel.component.smbj;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConsumer;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileOperations;

import java.util.List;

public class SmbConsumer extends GenericFileConsumer<SmbFile> {

    private final String endpointPath;
    private final String currentRelativePath = "";
    private GenericFileConverter genericFileConverter;

    public SmbConsumer(GenericFileEndpoint<SmbFile> endpoint, Processor processor, GenericFileOperations<SmbFile> operations) {
        super(endpoint, processor, operations);
        SmbConfiguration config = (SmbConfiguration) endpoint.getConfiguration();
        this.endpointPath = config.getShare() + "\\" + config.getPath();
        genericFileConverter = new GenericFileConverter();
    }

    @Override
    protected boolean pollDirectory(String fileName, List<GenericFile<SmbFile>> fileList, int depth) {
        int currentDepth = depth + 1;
        if (log.isTraceEnabled()) {
            log.trace("pollDirectory() running. My delay is [" + this.getDelay() + "] and my strategy is [" + this.getPollStrategy().getClass().toString() + "]");
            log.trace("pollDirectory() fileName[" + fileName + "]");
        }

        List<SmbFile> smbFiles = operations.listFiles(fileName);
        for (SmbFile smbFile : smbFiles) {
            //stop polling files if the limit is reached
            if (!canPollMoreFiles(fileList)) {
                return false;
            }
            GenericFile<SmbFile> gf = genericFileConverter.asGenericFile(fileName, smbFile, endpointPath, currentRelativePath);
            if (gf.isDirectory()) {
                if (endpoint.isRecursive() && currentDepth < endpoint.getMaxDepth()) {
                    //recursive scan of the subdirectory
                    String subDirName = fileName + "/" + gf.getFileName();
                    pollDirectory(subDirName, fileList, currentDepth);
                }
            } else {
                //conform to the minDepth parameter
                if (currentDepth < endpoint.getMinDepth())
                    continue;
                //TODO see if this check is necessary
                if (isValidFile(gf, false, smbFiles))
                    fileList.add(gf);
            }
        }
        return true;
    }

    @Override
    protected void updateFileHeaders(GenericFile<SmbFile> file, Message message) {
        //note: copied from FtpConsumer
        long length = file.getFile().getFileLength();
        long modified = file.getLastModified();
        file.setFileLength(length);
        file.setLastModified(modified);
        if (length >= 0) {
            message.setHeader(Exchange.FILE_LENGTH, length);
        }
        if (modified >= 0) {
            message.setHeader(Exchange.FILE_LAST_MODIFIED, modified);
        }
    }

    @Override
    protected boolean isMatched(GenericFile<SmbFile> file, String doneFileName, List<SmbFile> files) {
        return true;
    }
}
