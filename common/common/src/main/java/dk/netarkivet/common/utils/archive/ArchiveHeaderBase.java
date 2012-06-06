package dk.netarkivet.common.utils.archive;

import java.io.File;
import java.util.Map;
import java.util.Set;

public abstract class ArchiveHeaderBase {

	public abstract Object getHeaderValue(String key);

	public abstract String getHeaderStringValue(String key);

	public abstract Set<String> getHeaderFieldKeys();

	public abstract Map<String, Object> getHeaderFields();

	public abstract String getDate();

	public abstract long getLength();

	public abstract String getUrl();

	public abstract String getIp();

	public abstract String getMimetype();

	public abstract String getVersion();

	public abstract long getOffset();

	public abstract String getReaderIdentifier();

	public abstract String getRecordIdentifier();



	public abstract File getArchiveFile();

}