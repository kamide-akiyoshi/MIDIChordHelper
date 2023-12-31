package camidion.chordhelper.music;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 音名（オクターブ抜き）を表すクラスです。値は不変です。
 *
 * <p>この音名は、メジャーキーの調号にした場合に「♭、＃が何個つくか」という
 * 「五度圏インデックス」で保持することを基本としています。
 * こうすれば異名同音を明確に区別でき、しかも音楽理論的な計算を極めて単純な数式で行えるようになります。
 * この方式はMIDIメタメッセージで調号を指定するときにも使われていて、非常に高い親和性を持ちます。
 * </p>
 */
public class Note {
	/**
	 * 音階や調を表すシンボルの言語モードによる違いを定義します。
	 * <p>音名には、下記のようなインデックス（0～34）が割り当てられます。
	 * これは五度圏インデックスを負数にならないようシフトした値で、
	 * ダブルフラットからダブルシャープまでを含めたすべての音階をカバーしています。</p>
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
		 * @return 変化記号のインデックス
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
		 * インデックスに該当する音名を返します。
		 * @param index インデックス（定義は{@link Language}参照）
		 * @return 音名（例：Bb、B flat、変ロ）
		 * @throws IndexOutOfBoundsException インデックスが範囲を外れている場合
		 */
		private String stringOf(int index) {
			int sharpFlatIndex = index / 7;
			char note = notes.charAt(index - sharpFlatIndex * 7);
			String sharpFlat = sharpFlatList.get(sharpFlatIndex);
			return this == IN_JAPANESE ? sharpFlat + note : note + sharpFlat;
		}
		/**
		 * 音名に対するインデックスを返します。
		 * 日本語名（イロハニホヘト）はサポートしていません。
		 *
		 * @param noteSymbol 音名で始まる文字列（音名は大文字・小文字どちらも可）
		 * @return インデックス（定義は{@link Language}参照）
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
		 * 調の文字列表現を返します。メジャー／マイナーの区別がない場合、両方の表現を返します（例： "A / F#m"）。
		 * @param key 対象の調
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
	private static final int INDEX_OF_A = Language.SYMBOL.indexOf("A");
	private static final int INDEX_OF_C = Language.SYMBOL.indexOf("C");
	/** メジャーキー基準の五度圏インデックス */
	private int majorCo5;
	/** ノート番号（0～11） */
	private int noteNumber;
	/**
	 * １オクターブの半音数
	 */
	public static final int SEMITONES_PER_OCTAVE = 12;
	/**
	 * 五度圏インデックス（メジャーキー基準）から音名を構築します。
	 * @param majorCo5 五度圏インデックス
	 */
	public Note(int majorCo5) {
		noteNumber = mod12(toggleCo5(this.majorCo5 = majorCo5));
	}
	/**
	 * 音名を文字列から構築します。
	 * @param noteSymbol 音名の文字列
	 * @throws NullPointerException 引数がnullの場合
	 * @throws IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
	 */
	public Note(String noteSymbol) { this(co5Of(noteSymbol)); }
	/**
	 * この音階が指定されたオブジェクトと等しいか調べます。
	 *
	 * <p>双方の五度圏インデックスが等しい場合のみtrueを返します。
	 * すなわち、異名同音は等しくないものとして判定されます。
	 * </p>
	 *
	 * @return この音階が指定されたオブジェクトと等しい場合true
	 */
	@Override
	public boolean equals(Object anObject) {
		if( this == anObject ) return true;
		if( ! (anObject instanceof Note) ) return false;
		return majorCo5 == ((Note)anObject).majorCo5;
	}
	/**
	 * この音階のハッシュコード値として、五度圏インデックス値をそのまま返します。
	 * @return この音階のハッシュコード値
	 */
	@Override
	public int hashCode() { return majorCo5; }
	/**
	 * 音階が等しいかどうかを、異名同音を無視して判定します。
	 * @param another 比較対象の音階
	 * @return 等しければtrue
	 */
	public boolean equalsEnharmonically(Note another) {
		return this == another || another != null && this.noteNumber == another.noteNumber;
	}
	/**
	 * 五度圏インデックス（メジャーキー基準）を返します。
	 * @return 五度圏インデックス
	 */
	public int toCo5() { return majorCo5; }
	/**
	 * この音階に対応するMIDIノート番号（0オリジン表記）の最小値（オクターブの最も低い値）を返します。
	 * @return MIDIノート番号の最小値（0～11）
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
	 * @throws NullPointerException 言語モードがnullの場合
	 */
	public String toStringIn(Language language) {
		return language.stringOf(majorCo5 + INDEX_OF_C);
	}
	/**
	 * 引数で指定された音名のメジャーキー基準の五度圏インデックスを返します。
	 * @param noteSymbol 音名の文字列
	 * @return メジャーキー基準の五度圏インデックス
	 * @throws NullPointerException 引数がnullの場合
	 * @throws IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
	 */
	public static int co5Of(String noteSymbol) {
		return Language.SYMBOL.indexOf(noteSymbol) - INDEX_OF_C;
	}
	/**
	 * 音階を表す下記2種類の順序値（インデックス）を切り替えます。
	 * <ul>
	 * <li>五度圏インデックス（メジャーキー基準）： メジャーキーにおける調号の♯の数（♭の場合負数）を表す値</li>
	 * <li>半音インデックス： MIDIノート番号からオクターブ成分を抜いた値</li>
	 * </ul>
	 * <p>どちらのインデックスも 0 が C を表します。</p>
	 * <p>実際の処理は、下記に示すシンプルなものです。</p>
	 * <ul>
	 * <li>元の値が奇数の場合： 6（半オクターブ＝6半音）を足した値を返す</li>
	 * <li>元の値が偶数の場合： 元の値をそのまま返す</li>
	 * </ul>
	 * <p>戻り値は必ずしも0～11の範囲に入るとは限りません。
	 * 値の範囲を明確ににするには {@link #mod12(int)} を併用する必要があります。
	 * </p>
	 *
	 * @param i 元の値
	 * @return 切り替え後の値
	 */
	public static int toggleCo5(int i) { return (i & 1) == 0 ? i : i+6 ; }
	/**
	 * MIDIノート番号からオクターブ成分を抜きます。
	 * <p>n % 12 と似ていますが、Java の % 演算子では、左辺に負数を与えると答えも負数になってしまうため、
	 * n % 12 で計算しても 0～11 の範囲を外れてしまうことがあります。
	 * そこで、負数の場合に12を足すことにより 0～11 の範囲に入るよう補正します。
	 * </p>
	 * @param n 元のノート番号
	 * @return オクターブ成分を抜いたノート番号（0～11）
	 */
	public static int mod12(int n) {
		int qn = n % SEMITONES_PER_OCTAVE;
		return qn < 0 ? qn + 12 : qn ;
	}
	/**
	 * 五度圏インデックスで表された音階を、指定された半音数だけ移調した結果を返します。
	 *
	 * <p>移調する半音数が０の場合、指定の五度圏インデックスをそのまま返します。
	 * それ以外の場合、移調結果を -5 ～ 6 の範囲で返します。
	 * </p>
	 *
	 * @param co5 五度圏インデックス
	 * @param chromaticOffset 移調する半音数
	 * @return 移調後の五度圏インデックス
	 */
	public static int transposeCo5(int co5, int chromaticOffset) {
		if( chromaticOffset == 0 ) return co5;
		int transposedCo5 = mod12( co5 + toggleCo5(chromaticOffset) );
		if( transposedCo5 > 6 ) transposedCo5 -= Note.SEMITONES_PER_OCTAVE;
		return transposedCo5;
	}
	/**
	 * 指定の五度圏インデックスの真裏にあたる値を返します。
	 * @param co5 五度圏インデックス
	 * @return 真裏の五度圏インデックス
	 */
	public static int oppositeCo5(int co5) { return co5 > 0 ? co5 - 6 : co5 + 6; }
	/**
	 * MIDIノート番号が示す音名を返します。
	 * <p>白鍵の場合は A ～ G までの文字、黒鍵の場合は＃と♭の両方の表現を返します。
	 * ただし、制限文字数の指定により＃と♭の両方を返せないことがわかった場合、
	 * 五度圏のC/Amに近いキーでよく使われるほうの表記（C# Eb F# Ab Bb）だけを返します。
	 * </p>
	 * <p>ノート番号だけでは物理的な音階情報しか得られないため、
	 * 白鍵で＃♭のついた音階表現（B#、Cb など）、ダブルシャープ、ダブルフラットを使った表現は返しません。
	 * </p>
	 * @param noteNumber MIDIノート番号
	 * @param maxChars 最大文字数（無制限の場合nullを指定）
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNumberToSymbol(int noteNumber, Integer maxChars) {
		int co5 = mod12(toggleCo5(noteNumber));
		if( co5 == 11 ) co5 -= Note.SEMITONES_PER_OCTAVE; // E# -> F
		if( co5 < 6 ) return Language.SYMBOL.stringOf(co5 + INDEX_OF_C); // F C G D A E B

		if( maxChars == null || maxChars >= "F# / Gb".length() ) return
				Language.SYMBOL.stringOf(co5 + INDEX_OF_C) + " / " +
				Language.SYMBOL.stringOf(co5 + INDEX_OF_C - Note.SEMITONES_PER_OCTAVE);

		if( co5 >= 8 ) co5 -= Note.SEMITONES_PER_OCTAVE; // G# -> Ab, D# -> Eb, A# -> Bb
		return Language.SYMBOL.stringOf(co5 + INDEX_OF_C); // C# Eb F# Ab Bb
	}
	/**
	 * MIDIノート番号が示す音名を返します。
	 * 内部的には{@link #noteNumberToSymbol(int,Integer)}を呼び出しているだけです。
	 * </p>
	 * @param noteNumber MIDIノート番号
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNumberToSymbol(int noteNumber) {
		return noteNumberToSymbol(noteNumber, null);
	}
}
