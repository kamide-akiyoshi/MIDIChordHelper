package camidion.chordhelper.music;

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
	private static final int INDEX_OF_A = NoteSymbolLanguage.SYMBOL.indexOf("A");
	private static final int INDEX_OF_C = NoteSymbolLanguage.SYMBOL.indexOf("C");
	/**
	 * メジャーキー基準の五度圏インデックス値
	 */
	private int majorCo5;
	/**
	 * ノート番号（0～11）
	 */
	private int noteNumber;
	/**
	 * 音名 C（ハ音）を構築します。
	 */
	public NoteSymbol() {
	}
	/**
	 * 五度圏インデックス値（メジャーキー基準）から音名を構築します。
	 * @param majorCo5 五度圏インデックス値
	 */
	public NoteSymbol(int majorCo5) {
		noteNumber = toNoteNumber(this.majorCo5 = majorCo5);
	}
	/**
	 * 音名を文字列から構築します。
	 * @param noteSymbol 音名の文字列
	 * @throws IllegalArgumentException
	 *  指定の音名が空、またはA～Gの範囲を外れている場合
	 */
	public NoteSymbol(String noteSymbol) {
		this(NoteSymbolLanguage.SYMBOL.indexOf(noteSymbol) - INDEX_OF_C);
	}
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
	 * メジャーかマイナーかを指定し、対応する五度圏インデックス値を返します。
	 * <p>マイナーの場合、
	 * メジャー基準の五度圏インデックス値から３が差し引かれます。
	 * 例えば、C major の場合は調号が０個なのに対し、
	 * C minor のときは調号の♭が３個に増えますが、
	 * ３を差し引くことによってこのズレが補正されます。
	 * </p>
	 *
	 * @param isMinor マイナーのときtrue
	 * @return 五度圏インデックス値
	 */
	public int toCo5(boolean isMinor) { return isMinor ? majorCo5 - 3 : majorCo5; }
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
	public String toString() { return toStringIn(NoteSymbolLanguage.SYMBOL); }
	/**
	 * 指定した言語モードにおける文字列表現を返します。
	 * @param language 言語モード
	 * @return 文字列表現
	 */
	public String toStringIn(NoteSymbolLanguage language) {
		return toStringIn(language, false);
	}
	/**
	 * 指定した言語モードとメジャーマイナー種別における文字列表現を返します。
	 * <p>マイナーが指定された場合、五度圏インデックス値を３つ進めた音階として返します。
	 * 例えば、{@link #toCo5()} の戻り値が０の場合、
	 * メジャーが指定されていれば C を返しますが、
	 * マイナーが指定されると A を返します。
	 * これにより、同じ五度圏インデックス値０で C と Am の両方のルート音を導出できます。
	 * </p>
	 * @param language 言語モード
	 * @param isMinor マイナーのときtrue
	 * @return 文字列表現
	 */
	public String toStringIn(NoteSymbolLanguage language, boolean isMinor) {
		int index = majorCo5 + (isMinor ? INDEX_OF_A : INDEX_OF_C);
		if( index < 0 || index >= 35 ) {
			// インデックスOB発生の恐れがある場合
			// 調号 5b ～ 6# の範囲に収まるルート音となるような異名同音(enharmonic)に置き換える
			index = Music.mod12(index);  // returns 0(Fbb) ... 7(Fb) 8(Cb) 9(Gb) 10(Db) 11(Ab)
			if( isMinor ) {
				// 18(Am)
				if( index == 0 )
					index += Music.SEMITONES_PER_OCTAVE * 2; // 0(Fbbm) -> 24(D#m 5#)
				else
					index += Music.SEMITONES_PER_OCTAVE;  // 1(Cbbm) -> 13(Bbm 5b)
			}
			else {
				// 15(C)
				if( index < 10 )  // 0(Fbb) -> 12(Eb 3b), 9(Gb) -> 21(F# 6#)
					index += Music.SEMITONES_PER_OCTAVE;
			}
		}
		return language.stringOf(index);
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
	 * @param maxChars 最大文字数
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNumberToSymbol(int noteNumber, int maxChars) {
		int co5 = Music.mod12(Music.reverseCo5(noteNumber));
		if( co5 == 11 ) co5 -= Music.SEMITONES_PER_OCTAVE; // E# -> F
		if( co5 < 6 ) return (new NoteSymbol(co5)).toString(); // F C G D A E B

		if( maxChars >= "F# / Gb".length() ) return
				(new NoteSymbol(co5)).toString() + " / " +
				(new NoteSymbol(co5 - Music.SEMITONES_PER_OCTAVE)).toString();

		if( co5 >= 8 ) co5 -= Music.SEMITONES_PER_OCTAVE; // G# -> Ab, D# -> Eb, A# -> Bb
		return (new NoteSymbol(co5)).toString(); // C# Eb F# Ab Bb
	}
	/**
	 * 最大256文字の範囲で、MIDIノート番号が示す音名を返します。
	 * <p>内部的には
	 * {@link #noteNumberToSymbol(int, int)} を呼び出しているだけです。
	 * </p>
	 * @param noteNo MIDIノート番号
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNoToSymbol(int noteNo) {
		return noteNumberToSymbol(noteNo, 256);
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
	public static int toNoteNumber(int majorCo5) {
		return Music.mod12(Music.reverseCo5(majorCo5));
	}
}