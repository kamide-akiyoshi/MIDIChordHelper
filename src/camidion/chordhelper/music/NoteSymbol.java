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
		this(SymbolLanguage.SYMBOL.majorCo5Of(noteSymbol.trim()));
	}
	@Override
	protected NoteSymbol clone() {
		return new NoteSymbol(majorCo5);
	}
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
		if( this == anObject )
			return true;
		if( anObject instanceof NoteSymbol ) {
			NoteSymbol another = (NoteSymbol) anObject;
			return majorCo5 == another.majorCo5;
		}
		return false;
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
	public int toCo5(boolean isMinor) {
		return isMinor ? majorCo5 - 3 : majorCo5;
	}
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
	public String toString() {
		return toStringIn(SymbolLanguage.SYMBOL, false);
	}
	/**
	 * 指定した言語モードにおける文字列表現を返します。
	 * @param language 言語モード
	 * @return 文字列表現
	 */
	public String toStringIn(SymbolLanguage language) {
		return toStringIn(language, false);
	}
	/**
	 * 指定した言語モードとメジャーマイナー種別における文字列表現を返します。
	 * <p>マイナーが指定された場合、
	 * 五度圏インデックス値を３つ進めた音階として返します。
	 * 例えば、{@link #toCo5()} の戻り値が０の場合、
	 * メジャーが指定されていれば C を返しますが、
	 * マイナーが指定されると A を返します。
	 * これにより、同じ五度圏インデックス値０で C と Am の両方のルート音を導出できます。
	 * </p>
	 * @param language 言語モード
	 * @param isMinor マイナーのときtrue
	 * @return 文字列表現
	 */
	public String toStringIn(SymbolLanguage language, boolean isMinor) {
		int co5_s771 = majorCo5 + 15; // Shift 7 + 7 + 1 = 15 steps
		if( isMinor ) {
			// When co5 is for minor (key or chord), shift 3 steps more
			co5_s771 += 3;
		}
		if( co5_s771 < 0 || co5_s771 >= 35 ) {
			//
			// ３５種類の音名の範囲に入らないような値が来てしまった場合は、
			// それを調号として見たときに 5b ～ 6# の範囲に収まるような異名同音(enharmonic)に置き換える。
			//
			co5_s771 = Music.mod12(co5_s771);  // returns 0(Fbb) ... 7(Fb) 8(Cb) 9(Gb) 10(Db) 11(Ab)
			if( isMinor ) {
				if( co5_s771 == 0 )
					co5_s771 += Music.SEMITONES_PER_OCTAVE * 2; // 0(Fbbm)+24 = 24(D#m)
				else
					co5_s771 += Music.SEMITONES_PER_OCTAVE;  // 1(Cbbm)+12 = 13(Bbm)
			}
			else {
				if( co5_s771 < 10 )
					co5_s771 += Music.SEMITONES_PER_OCTAVE;  // 0(Fbb)+12 = 12(Eb), 9(Gb)+12 = 21(F#)
			}
		}
		int sharpFlatIndex = co5_s771 / 7;
		return language.toNoteSymbol(co5_s771 - sharpFlatIndex * 7, sharpFlatIndex);
	}
	/**
	 * 指定の最大文字数の範囲で、MIDIノート番号が示す音名を返します。
	 * <p>ノート番号だけでは物理的な音階情報しか得られないため、
	 * 白鍵で＃♭のついた音階表現（B#、Cb など）、
	 * ダブルシャープ、ダブルフラットを使った表現は返しません。
	 * </p>
	 * <p>白鍵の場合は A ～ G までの文字、黒鍵の場合は＃と♭の両方の表現を返します。
	 * ただし、制限文字数の指定により、＃と♭の両方を返せないことがわかった場合は、
	 * 五度圏で値０（キー C / Am）からの距離が、メジャー、マイナーの両方を含めて
	 * 近くにあるほうの表現（C# Eb F# Ab Bb）のみを返します。
	 * </p>
	 * @param noteNo MIDIノート番号
	 * @param maxChars 最大文字数
	 * @return MIDIノート番号が示す音名
	 */
	public static String noteNumberToSymbol(int noteNo, int maxChars) {
		int co5 = Music.mod12(Music.reverseCo5(noteNo));
		if( co5 == 11 ) {
			return (new NoteSymbol(-1)).toString();
		}
		else if( co5 >= 6 ) {
			if( maxChars >= 7 ) {
				return
					(new NoteSymbol(co5)).toString() + " / " +
					(new NoteSymbol(co5 - 12)).toString();
			}
			else {
				// String capacity not enough
				// Select only one note (sharped or flatted)
				return (new NoteSymbol(co5 - ((co5 >= 8) ? 12 : 0))).toString();
			}
		}
		else return (new NoteSymbol(co5)).toString();
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