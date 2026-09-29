package com.questadb;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.*;

public class AdbConnectionManager extends AbsAdbConnectionManager {
    private static AdbConnectionManager I;

    public static synchronized AdbConnectionManager getInstance(Context c) throws Exception {
        if (I == null) I = new AdbConnectionManager(c.getApplicationContext());
        return I;
    }

    private PrivateKey key;
    private Certificate cert;

    private AdbConnectionManager(Context c) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        key = readKey(c);
        cert = readCert(c);
        if (key == null || cert == null) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048, new SecureRandom());
            KeyPair p = g.generateKeyPair();
            key = p.getPrivate();
            PublicKey pub = p.getPublic();

            String alg = "SHA512withRSA";
            Date a = new Date();
            Date z = new Date(System.currentTimeMillis() + 31536000000L);
            X500Name n = new X500Name("CN=QuestADB");

            CertificateExtensions e = new CertificateExtensions();
            e.set("SubjectKeyIdentifier",
                    new SubjectKeyIdentifierExtension(new KeyIdentifier(pub).getIdentifier()));
            e.set("PrivateKeyUsage", new PrivateKeyUsageExtension(a, z));

            X509CertInfo i = new X509CertInfo();
            i.set("version", new CertificateVersion(2));
            i.set("serialNumber",
                    new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            i.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(alg)));
            i.set("subject", new CertificateSubjectName(n));
            i.set("key", new CertificateX509Key(pub));
            i.set("validity", new CertificateValidity(a, z));
            i.set("issuer", new CertificateIssuerName(n));
            i.set("extensions", e);

            X509CertImpl x = new X509CertImpl(i);
            x.sign(key, alg);
            cert = x;

            writeKey(c, key);
            writeCert(c, cert);
        }
    }

    @NonNull
    protected PrivateKey getPrivateKey() {
        return key;
    }

    @NonNull
    protected Certificate getCertificate() {
        return cert;
    }

    @NonNull
    protected String getDeviceName() {
        return "QuestADB";
    }

    private static Certificate readCert(Context c) throws Exception {
        File f = new File(c.getFilesDir(), "cert.pem");
        if (!f.exists()) return null;
        try (InputStream i = new FileInputStream(f)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(i);
        }
    }

    private static void writeCert(Context c, Certificate x) throws Exception {
        try (OutputStream o = new FileOutputStream(new File(c.getFilesDir(), "cert.pem"))) {
            o.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            o.write('\n');
            new BASE64Encoder().encode(x.getEncoded(), o);
            o.write('\n');
            o.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static PrivateKey readKey(Context c) throws Exception {
        File f = new File(c.getFilesDir(), "private.key");
        if (!f.exists()) return null;
        byte[] b = new byte[(int) f.length()];
        try (InputStream i = new FileInputStream(f)) {
            i.read(b);
        }
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(b));
    }

    private static void writeKey(Context c, PrivateKey k) throws Exception {
        try (OutputStream o = new FileOutputStream(new File(c.getFilesDir(), "private.key"))) {
            o.write(k.getEncoded());
        }
    }
}
