package com.floragunn.searchguard.tools.tlstool.tasks;

import java.security.KeyPair;
import java.util.Date;

import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.floragunn.searchguard.tools.tlstool.Config;
import com.floragunn.searchguard.tools.tlstool.Context;
import com.floragunn.searchguard.tools.tlstool.ToolException;

public class CreateNodeCertificate extends CreateNodeCertificateBase {
	private Config.Node nodeConfig;

	public CreateNodeCertificate(Context ctx, Config.Node nodeConfig) {
		super(ctx, nodeConfig);
		this.nodeConfig = nodeConfig;
	}

	@Override
	public void run() throws ToolException {
		try {
			KeyPair nodeKeyPair = generateKeyPair(nodeConfig.getKeysize());

			SubjectPublicKeyInfo subPubKeyInfo = ctx.getSigningCertificate().getSubjectPublicKeyInfo();
			X500Name subjectName = createDn(nodeConfig.getDn(), "node");

			X509v3CertificateBuilder builder = new X509v3CertificateBuilder(ctx.getSigningCertificate().getSubject(),
					ctx.nextId(), new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis() + 730), // TODO
					subjectName, subPubKeyInfo);

			JcaX509ExtensionUtils extUtils = getExtUtils();

			builder.addExtension(Extension.authorityKeyIdentifier, false,
					extUtils.createAuthorityKeyIdentifier(ctx.getSigningCertificate()))
					.addExtension(Extension.subjectKeyIdentifier, false,
							extUtils.createSubjectKeyIdentifier(nodeKeyPair.getPublic()))
					.addExtension(Extension.basicConstraints, true, new BasicConstraints(0))
					.addExtension(Extension.keyUsage, true,
							new KeyUsage(
									KeyUsage.digitalSignature | KeyUsage.nonRepudiation | KeyUsage.keyEncipherment))
					.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(
							new KeyPurposeId[] { KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth }));

			builder.addExtension(Extension.subjectAlternativeName, false,
					new DERSequence(createSubjectAlternativeNameList()));

			X509CertificateHolder nodeCertificate = builder.build(new JcaContentSignerBuilder("SHA1withRSA")
					.setProvider(ctx.getSecurityProvider()).build(nodeKeyPair.getPrivate()));

			addOutputFile(getNodeFileName(nodeConfig) + ".key", nodeKeyPair.getPrivate());
			addOutputFile(getNodeFileName(nodeConfig) + ".pem", ctx.getSigningCertificate(), nodeCertificate);

		} catch (CertIOException | OperatorCreationException e) {
			throw new ToolException("Error while composing certificate", e);
		}
	}

}
