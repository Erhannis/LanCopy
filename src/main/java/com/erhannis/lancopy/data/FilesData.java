/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy.data;

import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarOutputStream;

/**
 *
 * @author erhannis
 */
public class FilesData extends Data {
  private static final Charset UTF8 = Charset.forName("UTF-8");

  public final File[] files;

  public FilesData(File[] files) {
    this.files = files;
  }

  @Override
  public String getMime() {
    return "lancopy/files";
  }

  @Override
  public String toString() {
    String[] subtexts = new String[files.length];
    for (int i = 0; i < files.length; i++) {
      subtexts[i] = files[i].getName() + " (" + files[i].length() + ")";
    }
    return "[files] {" + String.join(", ", subtexts) + "}";
  }

  private static class PathedFile {
    public final String path;
    public final File file;

    public PathedFile(String path, File file) {
      this.path = path;
      this.file = file;
    }
  }

  @Override
  public InputStream serialize() {
    if (files.length == 1) {
      try {
        System.err.println("//TODO Handle filename");
        return new FileInputStream(files[0]);
      } catch (FileNotFoundException ex) {
        Logger.getLogger(FilesData.class.getName()).log(Level.SEVERE, null, ex);
        return new ErrorData("Error serializing files: " + ex.getMessage()).serialize();
      }
    }
    
    //TODO Allow actual streaming, so we don't have to load the files into memory
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    LinkedList<PathedFile> pending = new LinkedList<>();
    for (File f : files) {
      pending.add(new PathedFile("", f));
    }

    try (TarOutputStream out = new TarOutputStream(baos)) {
      LinkedList<String> path = new LinkedList<>();
      while (!pending.isEmpty()) {
        PathedFile pf = pending.pop();
        if (pf.file.isDirectory()) {
          System.err.println("//TODO Handle directories");
          continue;
        }
        out.putNextEntry(new TarEntry(pf.file, pf.path + "/" + pf.file.getName()));
        BufferedInputStream origin = new BufferedInputStream(new FileInputStream(pf.file));
        int count;
        byte data[] = new byte[2048];

        while ((count = origin.read(data)) != -1) {
          out.write(data, 0, count);
        }

        out.flush();
        origin.close();
      }
    } catch (IOException ex) {
      Logger.getLogger(FilesData.class.getName()).log(Level.SEVERE, null, ex);
    }

    System.err.println("//TODO Handle filename");
    return new ByteArrayInputStream(baos.toByteArray());
  }

  public static Data deserialize(InputStream stream) {
    System.err.println("//TODO Handle filename");
    System.err.println("//TODO Handle saving");
    File f = new File("temp.dat");
    try {
      FileUtils.copyInputStreamToFile(stream, f);
    } catch (IOException ex) {
      Logger.getLogger(FilesData.class.getName()).log(Level.SEVERE, null, ex);
      return new ErrorData("Error deserializing files: " + ex);
    }
    return new FilesData(new File[]{f});
  }
}
