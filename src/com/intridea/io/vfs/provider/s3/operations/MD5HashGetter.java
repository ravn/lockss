package com.intridea.io.vfs.provider.s3.operations;

import org.apache.commons.vfs.FileSystemException;

import com.intridea.io.vfs.operations.IMD5HashGetter;
import com.intridea.io.vfs.provider.s3.S3FileObject;

/**
 * @author <A href="mailto:alexey at abashev dot ru">Alexey Abashev</A>
 * @version $Id: MD5HashGetter.java,v 1.1.2.1 2011-05-17 21:49:25 dshr Exp $
 */
public class MD5HashGetter implements IMD5HashGetter {
    private final S3FileObject file;

    /**
     * @param file
     */
    public MD5HashGetter(S3FileObject file) {
        this.file = file;
    }

    /* (non-Javadoc)
     * @see com.intridea.io.vfs.operations.IMD5HashGetter#getMD5Hash()
     */
    @Override
    public String getMD5Hash() throws FileSystemException {
        return file.getMD5Hash();
    }

    /* (non-Javadoc)
     * @see org.apache.commons.vfs.operations.FileOperation#process()
     */
    @Override
    public void process() throws FileSystemException {

        // Do nothing
    }
}
