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
	SYMBOL(
		Arrays.asList("bb","b","","#","x"),
		"FCGDAEB",false,"","m"," / "
	),
	/**
	 * 英名表記（B flat, F sharp）
	 */
	NAME(
		Arrays.asList(" double flat"," flat",""," sharp"," double sharp"),
		"FCGDAEB",false," major"," minor"," / "
	),
	/**
	 * 日本名表記（変ロ, 嬰ヘ）
	 */
	IN_JAPANESE(
		Arrays.asList("重変","変","","嬰","重嬰"),
		"ヘハトニイホロ",true,"長調","短調","／"
	);
	/**
	 * ♭や♯の表記を、半音下がる数が多いほうから順に並べたリスト
	 */
	List<String> sharpFlatList;
	/**
	 * 音名を五度圏順で並べた文字列（必ず７文字でなければならない）
	 */
	String notes;
	/**
	 * 変化記号が音名の前につく（true）か後ろにつく（false）か
	 * <p>英語の場合は B♭ のように♭が後ろ、
	 * 日本語の場合は「変ロ」のように「変」が前につくことを表します。
	 * </p>
	 */
	boolean preSharpFlat;
	/**
	 * メジャーを表す文字列
	 */
	String major;
	/**
	 * マイナーを表す文字列
	 */
	String minor;
	/**
	 * メジャーとマイナーを併記する場合の区切り文字
	 */
	String majorMinorDelimiter;
	private SymbolLanguage(
		List<String> sharpFlatList,
		String notes,
		boolean preSharpFlat,
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
	 * 音名の文字列を、メジャーキー基準の五度圏インデックス値に変換します。
	 *
	 * @param noteSymbol 音名の文字列
	 * @return メジャーキー基準の五度圏インデックス値
	 * @throws IllegalArgumentException
	 *  指定の音名が空、またはA～Gの範囲を外れている場合
	 */
	public int majorCo5Of(String noteSymbol) {
		if( Objects.requireNonNull(
			noteSymbol,
			"Note symbol must not be null"
		).isEmpty() ) {
			throw new IllegalArgumentException(
				"Empty note symbol specified"
			);
		}
		char topChar = noteSymbol.charAt(0);
		int co5 = notes.indexOf(topChar);
		if( co5 < 0 ) {
			throw new IllegalArgumentException(
				"Unknown note symbol " + noteSymbol
			);
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
