package camidion.chordhelper.music;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 音階や調を表すシンボルの言語モードによる違いを定義します。
 * <p>音名には、下記のような五度圏順のインデックス値（0～34）が割り当てられます。
 * <pre>Fbb=0, Cbb=1, .. Bb=13, F=14, C=15, .. B=20, F#=21, C#=22, .. B#=27, Fx=28, .. Bx=34</pre>
 */
public enum NoteSymbolLanguage {
	/**
	 * 記号表記（Bb, F#）
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
	//
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
	 * ♭や♯の表記を、半音下がる数が多いほうから順に並べたリスト
	 */
	private List<String> sharpFlatList;
	/**
	 * 引数の先頭にある、♭や♯などの変化記号のインデックス(0～4)を返します。
	 * <p>変化記号がない場合、2 を返します。それ以外は次の値を返します。</p>
	 * <ul>
	 * <li>ダブルシャープ：4</li>
	 * <li>シャープ：3</li>
	 * <li>フラット：1</li>
	 * <li>ダブルフラット：0</li>
	 * </ul>
	 * @param s 変化記号で始まる文字列
	 * @return インデックス
	 */
	private int sharpFlatIndexOf(String s) {
		int index = 0;
		for( String sharpFlat : sharpFlatList ) {
			if( ! sharpFlat.isEmpty() && s.startsWith(sharpFlat) ) return index;
			index++;
		}
		return 2;
	}
	/**
	 * 音名を五度圏順で並べた7文字
	 */
	private String notes;
	/**
	 * インデックス値に該当する音名を返します。
	 * @param index インデックス値（定義は{@link NoteSymbolLanguage}参照）
	 * @return 音名（例：Bb、B flat、変ロ）
	 * @throws IndexOutOfBoundsException インデックス値が範囲を外れている場合
	 */
	public String stringOf(int index) {
		int sharpFlatIndex = index / 7;
		char note = notes.charAt(index - sharpFlatIndex * 7);
		String sharpFlat = sharpFlatList.get(sharpFlatIndex);
		return this == IN_JAPANESE ? sharpFlat + note : note + sharpFlat;
	}
	/**
	 * 音名に対するインデックス値を返します。
	 * 音名は通常、英大文字（ABCDEFG）ですが、英小文字（abcdefg）も認識します。
	 * 日本語名（イロハニホヘト）はサポートしていません。
	 *
	 * @param noteSymbol 音名で始まる文字列
	 * @return インデックス値（定義は{@link NoteSymbolLanguage}参照）
	 * @throws UnsupportedOperationException このオブジェクトが {@link #IN_JAPANESE} の場合
	 * @throws NullPointerException 引数がnullの場合
	 * @throws IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
	 */
	public int indexOf(String noteSymbol) {
		if( this == IN_JAPANESE ) throw new UnsupportedOperationException();
		Objects.requireNonNull(noteSymbol,"Musical note symbol must not be null");
		String trimmed = noteSymbol.trim();
		if( trimmed.isEmpty() ) throw new IllegalArgumentException("Empty musical note symbol");
		int noteIndex = notes.indexOf(Character.toUpperCase(trimmed.charAt(0)));
		if( noteIndex < 0 ) throw new IllegalArgumentException(
				"Unknown musical note symbol ["+noteSymbol+"] not in ["+notes+"]");
		return 7 * sharpFlatIndexOf(trimmed.substring(1)) + noteIndex;
	}
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
	/**
	 * 調の文字列表現を返します。メジャー／マイナーの区別が不明な場合、両方の表現を返します。
	 * @param note 音名
	 * @param majorMinor 負数:マイナー 0:不明 正数:メジャー
	 * @return 調の文字列表現
	 */
	public String keyStringOf(NoteSymbol note, int majorMinor) {
		String s = "";
		if( majorMinor >= 0 ) {
			s = note.toStringIn(this) + major;
			if( majorMinor > 0 ) return s;
			s += majorMinorDelimiter;
		}
		return s + note.toMinorKeyRootStringIn(this) + minor;
	}
}
