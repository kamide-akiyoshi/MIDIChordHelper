package camidion.chordhelper.music;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * シンボルの言語モード（音階、調など）
 */
public enum SymbolLanguage {
	/**
	 * シンボル表記（Bb, F#）
	 */
	SYMBOL(Arrays.asList("bb","b","","#","x"),"FCGDAEB",false,"","m"," / "),
	/**
	 * 英名表記（B flat, F sharp）
	 */
	NAME(Arrays.asList(" double flat"," flat",""," sharp"," double sharp"),
			"FCGDAEB",false," major"," minor"," / "),
	/**
	 * 日本名表記（変ロ, 嬰ヘ）
	 */
	IN_JAPANESE(Arrays.asList("重変","変","","嬰","重嬰"),
			"ヘハトニイホロ",true,"長調","短調","／");
	/**
	 * ♭や♯の表記を、半音下がる数が多いほうから順に並べたリスト
	 */
	private List<String> sharpFlatList;
	/**
	 * 音名を五度圏順で並べた文字列（必ず７文字でなければならない）
	 */
	private String notes;
	/**
	 * 変化記号が音名の前につく（true）か後ろにつく（false）か
	 * <p>英語の場合は B♭ のように♭が後ろ、
	 * 日本語の場合は「変ロ」のように「変」が前につくことを表します。
	 * </p>
	 */
	private boolean preSharpFlat;
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

	private SymbolLanguage(List<String> sharpFlatList, String notes, boolean preSharpFlat,
		String major, String minor, String majorMinorDelimiter
	) {
		this.sharpFlatList = sharpFlatList;
		this.notes = notes;
		this.preSharpFlat = preSharpFlat;
		this.major = major;
		this.minor = minor;
		this.majorMinorDelimiter = majorMinorDelimiter;
	}
	/**
	 * 音名を返します。
	 * @param noteIndex FCGDAEBの音名インデックス（0～6）
	 * @param sharpFlatIndex 0=ダブルフラット、1=フラット、2=ナチュラル、3=シャープ、4=ダブルシャープ
	 * @return 音名（例：Bb、B flat、変ロ）
	 */
	public String toNoteSymbol(int noteIndex, int sharpFlatIndex) {
		String note = notes.substring( noteIndex, noteIndex+1 );
		String sharpFlat = sharpFlatList.get(sharpFlatIndex);
		return preSharpFlat ? sharpFlat + note : note + sharpFlat;
	}
	/**
	 * 調の文字列表現を返します。
	 * @param note 音名
	 * @param majorMinor -1:マイナー 0:不明 1:メジャー
	 * @return 調の文字列表現
	 */
	public String toStringIn(NoteSymbol note, int majorMinor) {
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
	 * 音名の文字列を、メジャーキー基準の五度圏インデックス値に変換します。
	 * 音名は通常大文字ですが、小文字も認識します。
	 *
	 * @param noteSymbol 音名の文字列
	 * @return メジャーキー基準の五度圏インデックス値
	 * @throws
	 * IllegalArgumentException 指定された音名が空、または[A～G、a～g]の範囲を外れている場合
	 * @throws
	 * NullPointerException 指定された音名がnullの場合
	 */
	public int majorCo5Of(String noteSymbol) {
		if( Objects.requireNonNull(noteSymbol,"Musical note symbol must not be null").isEmpty() ) {
			throw new IllegalArgumentException("Empty musical note symbol");
		}
		int co5 = notes.indexOf(Character.toUpperCase(noteSymbol.charAt(0)));
		if( co5 < 0 ) {
			throw new IllegalArgumentException("Unknown musical note symbol ["+noteSymbol+"] not in ["+notes+"]");
		}
		co5--;
		int offset = -14;
		for( String sharpFlat : sharpFlatList ) {
			if( ! sharpFlat.isEmpty() && noteSymbol.startsWith(sharpFlat,1) ) {
				// 変化記号を発見
				// bb のほうが b よりも先にマッチするので誤判定の心配なし
				co5 += offset;
				break;
			}
			offset += 7;
		}
		return co5;
	}
}
