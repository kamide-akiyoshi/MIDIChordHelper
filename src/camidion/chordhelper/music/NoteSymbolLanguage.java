package camidion.chordhelper.music;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 音階シンボルの言語モードによる違いを定義します。
 * <p>各音階にはインデックス値が割り当てられます。
 * このインデックス値は、五度圏順で並んだ変化記号のない7音階（FCGDAEB）に対するインデックス値
 * x (0～6) と、
 * 変化記号（ダブルフラット、フラット、なし、シャープ、ダブルシャープ）に対するインデックス値
 * y (0～4) に基づいて、次の式で計算されます。</p>
 * <pre>インデックス値 = 5y + x</pre>
 * <p>このインデックス値の範囲は下記のように 0～34 となります。</p>
 * <pre>Fbb=0, Cbb=1, .. Bb=13, F=14, C=15, .. B=20, F#=21, C#=22, .. B#=27, Fx=28, .. Bx=34</pre>
 */
public enum NoteSymbolLanguage {
	/**
	 * シンボル表記（Bb, F#）
	 */
	SYMBOL(Arrays.asList("bb","b","","#","x"),"","m"),
	/**
	 * 英名表記（B flat, F sharp）
	 */
	NAME(Arrays.asList(" double flat"," flat",""," sharp"," double sharp")," major"," minor"),
	/**
	 * 日本名表記（変ロ, 嬰ヘ）
	 */
	IN_JAPANESE(Arrays.asList("重変","変","","嬰","重嬰"),"長調","短調");
	/**
	 * ♭や♯の表記を、半音下がる数が多いほうから順に並べたリスト
	 */
	private List<String> sharpFlatList;
	/**
	 * 音名を五度圏順で並べた7文字
	 */
	private String notes;
	/**
	 * メジャーを表す文字列
	 */
	private String major;
	/**
	 * マイナーを表す文字列
	 */
	private String minor;
	/**
	 * メジャーとマイナーを併記する場合の区切り文字
	 */
	private String majorMinorDelimiter;

	private NoteSymbolLanguage(List<String> sharpFlatList, String major, String minor) {
		if( (this.sharpFlatList = sharpFlatList).contains("変") ) {
			this.notes = "ヘハトニイホロ";
			this.majorMinorDelimiter = "／";
		} else {
			this.notes = "FCGDAEB";
			this.majorMinorDelimiter = " / ";
		}
		this.major = major;
		this.minor = minor;
	}
	/**
	 * インデックス値に該当する音名を返します。
	 * @param index インデックス値（定義は{@link NoteSymbolLanguage}参照）
	 * @return 音名（例：Bb、B flat、変ロ）
	 * @throws IndexOutOfBoundsException インデックスが範囲を外れている場合
	 */
	public String stringOf(int index) {
		int sharpFlatIndex = index / 7; // 0 1 2 3 4
		int noteSymbolIndex = index - sharpFlatIndex * 7; // 0 1 2 3 4 5 6
		String note = notes.substring(noteSymbolIndex, noteSymbolIndex+1);
		String sharpFlat = sharpFlatList.get(sharpFlatIndex);
		return this == IN_JAPANESE ? sharpFlat + note : note + sharpFlat;
	}
	/**
	 * 調の文字列表現を返します。メジャー／マイナーの区別が不明な場合、両方の表現を返します。
	 * @param note 音名
	 * @param majorMinor -1:マイナー 0:不明 1:メジャー
	 * @return 調の文字列表現
	 */
	public String keyStringOf(NoteSymbol note, int majorMinor) {
		String majorString = note.toStringIn(this, false) + major;
		if( majorMinor > 0 ) {
			return majorString;
		}
		else {
			String minorString = note.toStringIn(this, true) + minor;
			return majorMinor < 0 ? minorString : majorString + majorMinorDelimiter + minorString ;
		}
	}
	/**
	 * 音名の文字列（英字のみ）に対するインデックス値を返します。
	 * 音名は通常大文字ですが、小文字も認識します。
	 *
	 * @param noteSymbol 音名の文字列
	 * @return インデックス値（定義は{@link NoteSymbolLanguage}参照）
	 * @throws
	 * UnsupportedOperationException このオブジェクトが {@link #IN_JAPANESE} の場合
	 * @throws
	 * NullPointerException 指定された音名がnullの場合
	 * @throws
	 * IllegalArgumentException 指定された音名が空、または[A～G、a～g]の範囲を外れている場合
	 */
	public int indexOf(String noteSymbol) {
		if( this == IN_JAPANESE ) throw new UnsupportedOperationException();
		Objects.requireNonNull(noteSymbol,"Musical note symbol must not be null");
		String trimmed = noteSymbol.trim();
		if( trimmed.isEmpty() ) throw new IllegalArgumentException("Empty musical note symbol");
		char prefix = trimmed.charAt(0);
		int index = notes.indexOf(Character.toUpperCase(prefix));
		if( index < 0 ) {
			throw new IllegalArgumentException("Unknown musical note symbol ["+noteSymbol+"] not in ["+notes+"]");
		}
		String suffix = trimmed.substring(1);
		for( String sf : sharpFlatList ) {
			if( suffix.startsWith(sf) ) break; // bb が先にヒットするので b と間違える心配はない
			index += 7;
		}
		return index;
	}
}
