package camidion.chordhelper.music;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 音名（オクターブ抜き）を表すクラスです。値は不変です。
 *
 * <p>この音名は、メジャーキーの調号にした場合に
 * 「♭、＃が何個つくか」という数値
 * 「五度圏インデックス値」で保持することを基本としています。
 * こうすれば異名同音を明確に区別でき、
 * しかも音楽理論的な計算を極めて単純な数式で行えるようになります。
 * この方式はMIDIメタメッセージで調号を指定するときにも使われていて、
 * 非常に高い親和性を持ちます。
 * </p>
 */
public class NoteSymbol implements Cloneable {
	private static final int INDEX_OF_A = Language.SYMBOL.indexOf("A");
	private static final int INDEX_OF_C = Language.SYMBOL.indexOf("C");
	/**
	 * 音階や調を表すシンボルの言語モードによる違いを定義します。
	 * <p>音名には、下記のような五度圏順のインデックス値（0～34）が割り当てられます。
	 * <pre>Fbb=0, Cbb=1, .. Bb=13, F=14, C=15, .. B=20, F#=21, C#=22, .. B#=27, Fx=28, .. Bx=34</pre>
	 */
	public static enum Language {
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
		private Language(List<String> sharpFlatList, String major, String minor) {
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
		 * @param index インデックス値（定義は{@link Language}参照）
		 * @return 音名（例：Bb、B flat、変ロ）
		 * @throws IndexOutOfBoundsException インデックス値が範囲を外れている場合
		 */
		private String stringOf(int index) {
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
		 * @return インデックス値（定義は{@link Language}参照）
		 * @throws UnsupportedOperationException このオブジェクトが {@link #IN_JAPANESE} の場合
		 * @throws NullPointerException 引数がnullの場合
		 * @throws IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
		 */
		private int indexOf(String noteSymbol) {
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
		 * 調の文字列表現を返します。メジャー／マイナーの区別がない場合、両方の表現を返します。
		 * @param majorCo5 調の五度圏の値（0 == C/Am）
		 * @param majorMinor メジャー／マイナーの区別
		 * @return 調の文字列表現
		 */
		public String stringOf(Key key) {
			String s = "";
			int co5 = key.toCo5();
			Key.MajorMinor majorMinor = key.majorMinor();
			if( majorMinor.includes(Key.MajorMinor.MAJOR) ) {
				s = stringOf(co5 + INDEX_OF_C) + major;
				if( ! majorMinor.includes(Key.MajorMinor.MINOR) ) return s;
				s += majorMinorDelimiter;
			}
			return s + stringOf(co5 + INDEX_OF_A) + minor;
		}
	}
	/** メジャーキー基準の五度圏インデックス値 */
	private int majorCo5;
	/** ノート番号（0～11） */
	private int noteNumber;
	/** 音名 C（ハ音）を構築します。 */
	public NoteSymbol() { }
	/**
	 * 五度圏インデックス値（メジャーキー基準）から音名を構築します。
	 * @param majorCo5 五度圏インデックス値
	 */
	public NoteSymbol(int majorCo5) {
		noteNumber = majorCo5ToNoteNumber(this.majorCo5 = majorCo5);
	}
	/**
	 * 音名を文字列から構築します。
	 * @param noteSymbol 音名の文字列
	 * @throws IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
	 */
	public NoteSymbol(String noteSymbol) { this(co5OfSymbol(noteSymbol)); }
	@Override
	protected NoteSymbol clone() { return new NoteSymbol(majorCo5); }
	/**
	 * この音階が指定されたオブジェクトと等しいか調べます。
	 *
	 * <p>双方の五度圏インデックス値が等しい場合のみtrueを返します。
	 * すなわち、異名同音は等しくないものとして判定されます。
	 * </p>
	 *
	 * @return この音階が指定されたオブジェクトと等しい場合true
	 */
	@Override
	public boolean equals(Object anObject) {
		if( this == anObject ) return true;
		if( ! (anObject instanceof NoteSymbol) ) return false;
		return majorCo5 == ((NoteSymbol)anObject).majorCo5;
	}
	/**
	 * この音階のハッシュコード値として、
	 * 五度圏インデックス値をそのまま返します。
	 *
	 * @return この音階のハッシュコード値
	 */
	@Override
	public int hashCode() { return majorCo5; }
	/**
	 * 音階が等しいかどうかを、異名同音を無視して判定します。
	 * @param another 比較対象の音階
	 * @return 等しければtrue
	 */
	public boolean equalsEnharmonically(NoteSymbol another) {
		return this == another || this.noteNumber == another.noteNumber;
	}
	/**
	 * 五度圏インデックス値（メジャーキー基準）を返します。
	 * @return 五度圏インデックス値
	 */
	public int toCo5() { return majorCo5; }
	/**
	 * ノート番号（0～11）を返します。
	 * <p>これはMIDIノート番号からオクターブ情報を抜いた値と同じです。
	 * 五度圏インデックス値をノート番号に変換した場合、
	 * 異名同音、すなわち同じ音階が♭表記、♯表記のどちらだったか
	 * という情報は失われます。
	 * </p>
	 * @return ノート番号（0～11）
	 */
	public int toNoteNumber() { return noteNumber; }
	/**
	 * この音階の文字列表現として音名を返します。
	 * @return この音階の文字列表現
	 */
	@Override
	public String toString() { return toStringIn(Language.SYMBOL); }
	/**
	 * この音階の文字列表現を、引数で指定された言語モードで返します。
	 * @param language 言語モード
	 * @return 文字列表現
	 */
	public String toStringIn(Language language) {
		return language.stringOf(majorCo5 + INDEX_OF_C);
	}
	/**
	 * 引数で指定された音名のメジャーキー基準の五度圏インデックスを返します。
	 * @param noteSymbol 音名の文字列
	 * @return メジャーキー基準の五度圏インデックス
	 * @throws IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
	 */
	public static int co5OfSymbol(String noteSymbol) {
		return Language.SYMBOL.indexOf(noteSymbol) - INDEX_OF_C;
	}
	/**
	 * 指定の最大文字数の範囲で、MIDIノート番号が示す音名を返します。
	 * <p>白鍵の場合は A ～ G までの文字、黒鍵の場合は＃と♭の両方の表現を返します。
	 * ただし、制限文字数の指定により＃と♭の両方を返せないことがわかった場合、
	 * 五度圏のC/Amに近いキーでよく使われるほうの表記（C# Eb F# Ab Bb）だけを返します。
	 * </p>
	 * <p>ノート番号だけでは物理的な音階情報しか得られないため、
	 * 白鍵で＃♭のついた音階表現（B#、Cb など）、
	 * ダブルシャープ、ダブルフラットを使った表現は返しません。
	 * </p>
	 * @param noteNumber MIDIノート番号
	 * @param maxChars 最大文字数（最大がない場合は負数を指定）
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNumberToSymbol(int noteNumber, int maxChars) {
		int co5 = Music.mod12(Music.reverseCo5(noteNumber));
		if( co5 == 11 ) co5 -= Music.SEMITONES_PER_OCTAVE; // E# -> F
		if( co5 < 6 ) return (new NoteSymbol(co5)).toString(); // F C G D A E B

		if( maxChars < 0 || maxChars >= "F# / Gb".length() ) return
				(new NoteSymbol(co5)).toString() + " / " +
				(new NoteSymbol(co5 - Music.SEMITONES_PER_OCTAVE)).toString();

		if( co5 >= 8 ) co5 -= Music.SEMITONES_PER_OCTAVE; // G# -> Ab, D# -> Eb, A# -> Bb
		return (new NoteSymbol(co5)).toString(); // C# Eb F# Ab Bb
	}
	/**
	 * MIDIノート番号が示す音名を返します。
	 * 内部的には{@link #noteNumberToSymbol(int,int)}を呼び出しているだけです。
	 * </p>
	 * @param noteNumber MIDIノート番号
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNumberToSymbol(int noteNumber) {
		return noteNumberToSymbol(noteNumber, -1);
	}
	/**
	 * 指定された五度圏インデックス値（メジャーキー基準）を
	 * ノート番号（0～11）に変換します。
	 *
	 * <p>これはMIDIノート番号からオクターブ情報を抜いた値と同じです。
	 * 五度圏インデックス値をノート番号に変換した場合、
	 * 異名同音、すなわち同じ音階が♭表記、♯表記のどちらだったか
	 * という情報は失われます。
	 * </p>
	 * @return ノート番号（0～11）
	 */
	public static int majorCo5ToNoteNumber(int majorCo5) {
		return Music.mod12(Music.reverseCo5(majorCo5));
	}
}