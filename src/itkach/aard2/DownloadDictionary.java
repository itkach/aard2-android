package itkach.aard2;

public class DownloadDictionary {
    // the dictionary id
    private Integer dictionaryId;

    // the dictionary name
    private String dictionaryName;

    // the url address for downloading the dictionary
    private String dictionaryDownloadAddress;

    // Has this dictionary been downloaded or not
    private boolean isDownloaded;

    public DownloadDictionary(Integer id, String dName, String dDownloadAddress, boolean isD) {
        this.dictionaryId = id;
        this.dictionaryName = dName;
        this.dictionaryDownloadAddress = dDownloadAddress;
        this.isDownloaded = isD;
    }

    public Integer getDictionaryId() {
        return this.dictionaryId;
    }

    public String getDictionaryName() {
        return this.dictionaryName;
    }

    public String getDictionaryDownloadAddress() {
        return this.dictionaryDownloadAddress;
    }

    public boolean getIsDownloaded() {
        return this.isDownloaded;
    }

    public void setDownloaded(boolean newDownloadedStatus) {
        this.isDownloaded = newDownloadedStatus;
    }
}
