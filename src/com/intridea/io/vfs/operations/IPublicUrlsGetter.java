package com.intridea.io.vfs.operations;

import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.operations.FileOperation;

/**
 * File operation for gettin' direct urls to S3 objects.
 *
 * @author <A href="mailto:alexey at abashev dot ru">Alexey Abashev</A>
 * @version $Id: IPublicUrlsGetter.java,v 1.1.2.1 2011-05-17 21:49:25 dshr Exp $
 */
public interface IPublicUrlsGetter extends FileOperation {
    /**
     * Get direct http url to file.
     *
     * @return
     */
    String getHttpUrl();

    /**
     * Get private url in format s3://awsKey:awsSecretKey/bucket-name/object-name
     *
     * @return
     */
    String getPrivateUrl();

    /**
     *
     * @param expireInSeconds
     * @return
     */
    String getSignedUrl(int expireInSeconds) throws FileSystemException;
}
