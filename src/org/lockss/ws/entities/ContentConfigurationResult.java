/*
 * $Id: ContentConfigurationResult.java,v 1.1 2014-05-12 17:26:19 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2014 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 Except as contained in this notice, the name of Stanford University shall not
 be used in advertising or otherwise to promote the sale, use or other dealings
 in this Software without prior written authorization from Stanford University.

 */

/**
 * A wrapper for the result of a Content Configuration web service operation
 * over an archival unit.
 */
package org.lockss.ws.entities;

public class ContentConfigurationResult {
  private String id;
  private String name;
  private Boolean isSuccess;
  private String message;

  public ContentConfigurationResult() {
  }

  public ContentConfigurationResult(String id, String name, Boolean isSuccess,
      String message) {
    this.id = id;
    this.name = name;
    this.isSuccess = isSuccess;
    this.message = message;
  }

  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
  }
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public Boolean getIsSuccess() {
    return isSuccess;
  }
  public void setIsSuccess(Boolean isSuccess) {
    this.isSuccess = isSuccess;
  }
  public String getMessage() {
    return message;
  }
  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return "ContentConfigurationResult [id=" + id + ", name=" + name
	+ ", isSuccess=" + isSuccess + ", message=" + message + "]";
  }
}
