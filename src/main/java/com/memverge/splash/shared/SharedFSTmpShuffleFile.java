/*
 * Copyright (C) 2018 MemVerge Inc.
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
package com.memverge.splash.shared;

import com.memverge.splash.ShuffleFile;
import com.memverge.splash.TempFolder;
import com.memverge.splash.TmpShuffleFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedFSTmpShuffleFile extends SharedFSShuffleFile implements
    TmpShuffleFile {

  private static final Logger log = LoggerFactory
      .getLogger(SharedFSTmpShuffleFile.class);

  private static TempFolder folder = TempFolder.getInstance();

  private static final String TMP_FILE_PREFIX = "tmp-";
  private SharedFSShuffleFile commitTarget = null;

  private UUID uuid = null;

  private SharedFSTmpShuffleFile(String pathname) {
    super(pathname);
  }

  static SharedFSTmpShuffleFile make() {
    UUID uuid = UUID.randomUUID();
    String tmpPath = folder.getTmpPath();
    String filename = String.format("%s%s", TMP_FILE_PREFIX, uuid.toString());
    String fullPath = Paths.get(tmpPath, filename).toString();

    SharedFSTmpShuffleFile ret = new SharedFSTmpShuffleFile(fullPath);
    ret.uuid = uuid;
    return ret;
  }

  static SharedFSTmpShuffleFile make(ShuffleFile file) throws IOException {
    if (file == null) {
      throw new IOException("file is null");
    }
    if (!(file instanceof SharedFSShuffleFile)) {
      throw new IOException("only accept SharedFSShuffleFile");
    }
    SharedFSTmpShuffleFile ret = make();
    ret.commitTarget = (SharedFSShuffleFile) file;
    return ret;
  }

  @Override
  public TmpShuffleFile create() throws IOException {
    File parent = getFile().getParentFile();
    if (!parent.exists()) {
      boolean created = parent.mkdirs();
      if (!created) {
        log.info("parent folder {} creation return false.",
            parent.getAbsolutePath());
      }
    }
    boolean created = getFile().createNewFile();
    if (!created) {
      throw new IOException(String.format("file %s already exists.", getPath()));
    } else {
      log.debug("file {} created.", getPath());
    }
    return this;
  }

  @Override
  public void swap(TmpShuffleFile other) throws IOException {
    if (!other.exists()) {
      String message = "Can only swap with a uncommitted tmp file";
      throw new IOException(message);
    }

    SharedFSTmpShuffleFile otherLocal = (SharedFSTmpShuffleFile) other;

    delete();

    UUID tmpUuid = otherLocal.uuid;
    otherLocal.uuid = this.uuid;
    this.uuid = tmpUuid;

    File tmpFile = this.file;
    this.file = otherLocal.file;
    otherLocal.file = tmpFile;
  }

  @Override
  public SharedFSShuffleFile getCommitTarget() {
    return this.commitTarget;
  }

  @Override
  public ShuffleFile commit() throws IOException {
    if (commitTarget == null) {
      throw new IOException("No commit target.");
    } else if (!exists()) {
      create();
    }
    if (commitTarget.exists()) {
      log.warn("commit target already exists, remove '{}'.",
          commitTarget.getPath());
      commitTarget.delete();
    }
    log.debug("commit tmp file {} to target file {}.",
        getPath(), getCommitTarget().getPath());

    rename(commitTarget.getPath());
    return commitTarget;
  }

  @Override
  public void recall() {
    SharedFSShuffleFile commitTarget = getCommitTarget();
    if (commitTarget != null) {
      log.info("recall tmp file {} of target file {}.",
          getPath(), commitTarget.getPath());
    } else {
      log.info("recall tmp file {} without target file.", getPath());
    }
    delete();
  }

  @Override
  public OutputStream makeOutputStream() {
    try {
      create();
    } catch (IOException e) {
      String msg = String.format("Create file %s failed.", getPath());
      throw new IllegalArgumentException(msg, e);
    }
    OutputStream ret;
    try {
      ret = new FileOutputStream(file, false);
      log.debug("create output stream for {}.", getPath());
    } catch (FileNotFoundException e) {
      String msg = String.format("File %s not found?", getPath());
      throw new IllegalArgumentException(msg, e);
    }
    return ret;
  }

  @Override
  public UUID uuid() {
    return this.uuid;
  }
}
