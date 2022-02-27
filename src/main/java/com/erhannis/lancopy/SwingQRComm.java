/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.SwingQRCommFrame;
import com.erhannis.lancopy.DataOwner;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor2.CommChannel;
import java.util.Objects;

/**
 * I'm sorta tempted to make a "QRComm" abstract base, and have platform-specific
 * subclasses for `connect`, but it hardly seems worth it, and then I'm not sure if e.g. "equals"
 * or "type" are quite right....
 * 
 * @author erhannis
 */
public class SwingQRComm extends Comm {
    public static final String TYPE = "QR";
    
    public SwingQRComm(Advertisement owner) {
        super(owner, TYPE, 7);
    }

    private SwingQRComm() {
        this(null);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        SwingQRComm o = (SwingQRComm)obj;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @Override
    public String toString() {
        return super.toString()+"{"+"}";
    }

    @Override
    public Comm copyToOwner(Advertisement owner) {
        return new SwingQRComm(owner);
    }

    @Override
    public CommChannel connect(DataOwner dataOwner) throws Exception {
        SwingQRCommFrame sqcf = new SwingQRCommFrame(dataOwner, this);
        sqcf.setVisible(true);
        return sqcf.channel;
    }
}
