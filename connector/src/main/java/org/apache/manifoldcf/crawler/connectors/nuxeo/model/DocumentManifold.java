package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.nuxeo.client.api.NuxeoClient;
import org.nuxeo.client.api.objects.Document;
import org.nuxeo.client.api.objects.Documents;
import org.nuxeo.client.api.objects.Operation;
import org.nuxeo.client.api.objects.acl.ACE;
import org.nuxeo.client.api.objects.acl.ACL;
import org.nuxeo.client.api.objects.blob.Blob;

import com.google.common.collect.Maps;

public class DocumentManifold {

	private static final String URI_DOCUMENT = "SELECT * FROM Document";
	private static final String URI_TAGGING = "SELECT * FROM Tagging";

	private static final String DEFAULT_MIMETYPE = "text/html; charset=utf-8";
	private static final String[] avoid_properties = { "file:filename", "file:content", "files:files" };

	public Document document;
	public InputStream content;
	protected String mimetype;

	public static final String DELETED = "deleted";

	public static final String DOC_UID = "uid";
	public static final String DOC_ENTITY_TYPE = "entity-type";
	public static final String DOC_LAST_MODIFIED = "last-modified";
	public static final String DOC_STATE = "state";

	// Constructor
	public DocumentManifold(Document document) {
		this.document = document;
		processDocument();
	}

	/**
	 * 
	 * @param nuxeoClient
	 * @param date
	 * @param domains
	 * @param documentsType
	 * @param limit
	 * @param start
	 * @return Documents
	 */
	public static Documents getDocsByDate(NuxeoClient nuxeoClient, String date, List<String> domains,
			List<String> documentsType, int limit, int start) {

		String query = "";

		if (date == null || date.isEmpty()) {
			SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
			date = DATE_FORMAT.format(new Date(0));
		}
		query = "dc:modified > '" + date + "'";

		if (!domains.isEmpty()) {
			Iterator<String> itdom = domains.iterator();

			query = String.format(" %s AND ( ecm:path STARTSWITH '/%s'", query, itdom.next());

			while (itdom.hasNext()) {
				query = String.format("%s OR ecm:path STARTSWITH '/%s'", query, itdom.next());
			}

			query = String.format("%s )", query);
		}

		if (!documentsType.isEmpty()) {
			Iterator<String> itDocTy = documentsType.iterator();

			query = String.format(" %s AND ( ecm:primaryType = '%s'", query, itDocTy.next());

			while (itDocTy.hasNext()) {
				query = String.format("%s OR ecm:primaryType = '%s'", query, itDocTy.next());
			}

			query = String.format("%s )", query);
		}

		query = String.format(URI_DOCUMENT + " where %s ", query);

		nuxeoClient.header("X-NXDocumentProperties", "*");
		Documents docs = nuxeoClient.repository().query(query, String.valueOf(limit), String.valueOf(start), null, null,
				null, null);

		return docs;
	}

	/**
	 * 
	 * 
	 * @return Map<String, Object>
	 */
	public Map<String, Object> getMetadata() {
		Map<String, Object> docMetadata = Maps.newHashMap();

		for (Entry<String, Object> property : this.document.getProperties().entrySet()) {
			if (!Arrays.asList(avoid_properties).contains(property.getKey())) {
				addIfNotEmpty(docMetadata, property.getKey(), property.getValue());
			}
		}
		addIfNotEmpty(docMetadata, DOC_UID, this.document.getUid());
		addIfNotEmpty(docMetadata, DOC_ENTITY_TYPE, this.document.getEntityType());
		addIfNotEmpty(docMetadata, DOC_LAST_MODIFIED, this.document.getLastModified());
		addIfNotEmpty(docMetadata, DOC_STATE, this.document.getState());

		return docMetadata;
	}

	public void addIfNotEmpty(Map<String, Object> docMetadata, String key, Object obj) {
		if (obj != null && ((obj instanceof String && !((String) obj).isEmpty()) || !(obj instanceof String))) {
			docMetadata.put(key, obj);
		}
	}

	private void processDocument() {

		// Content
		InputStream is = null;
		String mimetype = null;
		try {
			is = document.fetchBlob().getStream();
			this.mimetype = (String) ((LinkedHashMap<?, ?>) this.getDocument().get("file:content")).get("mime-type");
		} catch (Exception ex) {
			is = new ByteArrayInputStream("".getBytes());
			mimetype = DEFAULT_MIMETYPE;
		}
		this.content = is;
		this.mimetype = mimetype;
	}

	// GETTERS AND SETERS
	public Document getDocument() {
		return this.document;
	}

	public String getMimeType() {
		return this.mimetype;
	}

	public int getLenght() {
		int size;
		try {
			size = this.getContent().available();
		} catch (IOException ex) {
			size = 0;
		}
		return size;
	}

	public InputStream getContent() {
		return this.content;
	}

	public String[] getPermissions(NuxeoClient nuxeoClient) {

		List<String> permissions = new ArrayList<String>();
		try {
			for (ACL acl : this.getDocument().fetchPermissions().getAcls()) {
				for (ACE ace : acl.getAces()) {
					if (ace.getStatus().equalsIgnoreCase("effective") && ace.getGranted().equalsIgnoreCase("true")) {
						permissions.add(ace.getUsername());
					}
				}
			}

			return permissions.toArray(new String[permissions.size()]);
		} catch (Exception e) {
			return new String[] {};
		}
	}

	public List<Attachment> getAttachments(NuxeoClient nuxeoClient) {
		List<Attachment> attachments = new ArrayList<Attachment>();
		List<?> arrayList = (List<?>) this.document.get(Attachment.ATT_KEY_FILES);

		for (Object object : arrayList) {
			Attachment attach = new Attachment();
			LinkedHashMap<?, ?> file = (LinkedHashMap<?, ?>) ((LinkedHashMap<?, ?>) object)
					.get(Attachment.ATT_KEY_FILE);

			attach.name = (String) file.get("name");
			attach.encoding = (String) file.get("encoding");
			attach.mime_type = (String) file.get("mime-type");
			attach.digestAlgorithm = (String) file.get("digestAlgorithm");
			attach.digest = (String) file.get("digest");
			attach.length = Long.valueOf((String) file.get("length"));
			attach.url = (String) file.get("data");

			try {
				Blob blob = nuxeoClient.repository().fetchBlobById(this.document.getUid(), 
						getAttachPath(attach.url));
				
				attach.data = blob.getStream();

			} catch (Exception ex) {
				attach.data = new ByteArrayInputStream("".getBytes());
			}

			attachments.add(attach);

		}

		return attachments;
	}

	public String getAttachPath(String absolutePath) {
		String[] splitPath = absolutePath.split("/");
		int size = splitPath.length;
		String path = String.join("/", splitPath[size - 4], splitPath[size - 3], splitPath[size - 2]);

		return path;
	}

	public String[] getTags(NuxeoClient nuxeoClient) {
		try {
			Operation op = nuxeoClient.automation("Repository.Query").param("query",
					URI_TAGGING + " where relation:source='" + this.document.getUid() + "'");
			Documents tags = op.execute();
			List<String> ls = new ArrayList<String>();

			for (Document tag : tags.getDocuments()) {
				ls.add(tag.getTitle());
			}
			return ls.toArray(new String[tags.size()]);
		} catch (Exception e) {
			return new String[] {};
		}
	}

}
