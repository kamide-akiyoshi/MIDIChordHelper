package camidion.chordhelper.music;


/**
 * 調（キー）を表すクラスです。
 *
 * <p>内部的には次の値を持っています。</p>
 * <ul>
 * <li>五度圏インデックス値。これは調号の♯の数（♭の数は負数）と同じです。</li>
 * <li>メジャー／マイナーの区別（無指定ありの３値）</li>
 * </ul>
 * <p>これらの値はMIDIのメタメッセージにある調号のパラメータに対応します。
 * </p>
 */
public class Key implements Cloneable {
	/**
	 * メジャーかマイナーかが特定できていないことを示す値
	 */
	public static final int MAJOR_OR_MINOR = 0;
	/**
	 * メジャーキー（長調）
	 */
	public static final int MAJOR = 1;
	/**
	 * マイナーキー（短調）
	 */
	public static final int MINOR = -1;
	/**
	 * この調の五度圏インデックス値
	 */
	private int co5;
	/**
	 * メジャー・マイナーの種別
	 */
	private int majorMinor;
	/**
	 * 調号が空のキー（ハ長調またはイ短調）を構築します。
	 */
	public Key() { setKey(0, MAJOR_OR_MINOR); }
	/**
	 * 指定の五度圏インデックス値を持つ調を、
	 * メジャーとマイナーを指定せずに構築します。
	 *
	 * @param co5 五度圏インデックス値
	 */
	public Key(int co5) { setKey(co5, MAJOR_OR_MINOR); }
	/**
	 * 指定の五度圏インデックス値を持つ、
	 * メジャー／マイナーを指定した調を構築します。
	 *
	 * @param co5 五度圏インデックス値
	 * @param majorMinor {@link #MAJOR}、{@link #MINOR}、{@link #MAJOR_OR_MINOR} のいずれか
	 */
	public Key(int co5, int majorMinor) {
		setKey(co5, majorMinor);
	}
	/**
	 * 指定の五度圏インデックス値を持つ、
	 * メジャー／マイナーの明確な調を構築します。
	 *
	 * @param co5 五度圏インデックス値
	 * @param isMinor true:マイナー、false:メジャー
	 */
	public Key(int co5, boolean isMinor) {
		setKey(co5, isMinor);
	}
	/**
	 * MIDIの調データ（メタメッセージ2byte）から調を構築します。
	 * @param midiData MIDIの調データ
	 */
	public Key(byte midiData[]) {
		setBytes(midiData);
	}
	/**
	 * C、Am のような文字列から調を構築します。
	 * @param keySymbol キーを表す文字列
	 */
	public Key(String keySymbol) {
		boolean isMinor = keySymbol.matches(".*m");
		setKey((new NoteSymbol(keySymbol)).toCo5(isMinor), isMinor);
	}
	/**
	 * 指定されたコードと同名の調を構築します。
	 * @param chord コード（和音）
	 */
	public Key(Chord chord) {
		boolean isMinor = chord.isSet(Chord.Interval.MINOR);
		setKey(chord.rootNoteSymbol().toCo5(isMinor), isMinor);
	}
	@Override
	public Key clone() {
		return new Key(co5, majorMinor);
	}
	@Override
	public boolean equals(Object anObject) {
		if( this == anObject )
			return true;
		if( anObject instanceof Key ) {
			Key another = (Key) anObject;
			return
				co5 == another.toCo5() &&
				majorMinor == another.majorMinor() ;
		}
		return false;
	}
	@Override
	public int hashCode() {
		return majorMinor * 256 + co5 ;
	}
	private void setKey(int co5, boolean isMinor) {
		setKey( co5, isMinor ? MINOR : MAJOR );
	}
	private void setKey(int co5, int majorMinor) {
		this.co5 = co5;
		this.majorMinor = majorMinor;
		normalize();
	}
	/**
	 * MIDIの調データ（メタメッセージ2byte）を設定します。
	 * @param data MIDIの調データ
	 */
	public void setBytes( byte[] data ) {
		byte sharpFlat = data.length > 0 ? data[0] : 0;
		byte isMinor = data.length > 1 ? data[1] : 0;
		setKey( (int)sharpFlat, isMinor==1 );
	}
	/**
	 * MIDIの調データ（メタメッセージ2byte）を生成して返します。
	 * @return  MIDIの調データ
	 */
	public byte[] getBytes() {
		byte data[] = new byte[2];
		data[0] = (byte)(co5 & 0xFF);
		data[1] = (byte)(majorMinor == MINOR ? 1 : 0);
		return data;
	}
	/**
	 * 五度圏インデックス値を返します。
	 * @return 五度圏インデックス値
	 */
	public int toCo5() { return co5; }
	/**
	 * メジャー／マイナーの区別を返します。
	 * @return {@link #MAJOR}、{@link #MINOR}、{@link #MAJOR_OR_MINOR} のいずれか
	 */
	public int majorMinor() { return majorMinor; }
	/**
	 * 相対ドの音階を返します。
	 * @return 相対ドの音階（0～11）
	 */
	public int relativeDo() {
		return NoteSymbol.toNoteNumber(co5);
	}
	/**
	 * この調のルート音を返します。
	 * メジャーキーの場合は相対ド、
	 * マイナーキーの場合は相対ラの音階です。
	 *
	 * @return キーのルート音（0～11）
	 */
	public int rootNoteNumber() {
		int n = relativeDo();
		return majorMinor==MINOR ? Music.mod12(n-3) : n;
	}
	/**
	 * 指定されたノート番号の音が、この調のスケールの構成音か調べます。
	 * メジャーキーの場合はメジャースケール、
	 * マイナーキーの場合はナチュラルマイナースケールとして判断されます。
	 *
	 * @param noteNumber ノート番号
	 * @return 指定されたノート番号がこのキーのスケールの構成音ならtrue
	 */
	public boolean isOnScale(int noteNumber) {
		return Music.isOnScale(noteNumber, co5);
	}
	/**
	 * この調を、指定された半音オフセット値だけ移調します。
	 *
	 * @param chromaticOffset 半音オフセット値
	 * @return このオブジェクト自身（移調後）
	 */
	public Key transpose(int chromaticOffset) {
		co5 = Music.transposeCo5(co5, chromaticOffset);
		return this;
	}
	/**
	 * この調に異名同音の調がある場合、その調に置換します。
	 * <p>例えば、♭５個（D♭メジャー）の場合は♯７個（C♯メジャー）に置換されます。
	 * 異名同音の調が存在しないキー（４♯～４♭）に対してこのメソッドを呼び出しても、
	 * 何も変化しません。
	 * </p>
	 */
	public void toggleEnharmonically() {
		if( co5 > 4 )
			co5 -= 12;
		else if( co5 < -4 )
			co5 += 12;
	}
	/**
	 * この調を正規化します。
	 * 調が７♭～７♯の範囲に入っていない場合、
	 * その範囲に入るよう調整されます。
	 */
	public void normalize() {
		if( co5 < -7 || co5 > 7 ) {
			co5 = Music.mod12( co5 );
			if( co5 > 6 ) co5 -= Music.SEMITONES_PER_OCTAVE;
		}
	}
	/**
	 * 平行調を生成して返します。
	 * これは元の調と同じ調号を持つ、メジャーとマイナーが異なる調です。
	 *
	 * <p>メジャーとマイナーの区別が不明な場合、クローンを生成したのと同じことになります。
	 * </p>
	 *
	 * @return 平行調
	 */
	public Key relativeKey() {
		return new Key(co5, majorMinor * (-1));
	}
	/**
	 * 同主調を生成して返します。
	 * これは元の調とルート音が同じで、メジャーとマイナーが異なる調です。
	 *
	 * <p>メジャーとマイナーの区別が不明な場合、クローンを生成したのと同じことになります。
	 * 元の調の♭、♯の数が５個以上の場合、
	 * ７♭～７♯の範囲をはみ出すことがあります（正規化は行われません）。
	 * </p>
	 *
	 * @return 同主調
	 */
	public Key parallelKey() {
		switch( majorMinor ) {
		case MAJOR: return new Key( co5-3, MINOR );
		case MINOR: return new Key( co5+3, MAJOR );
		default: return new Key(co5);
		}
	}
	/**
	 * 五度圏で真裏にあたる調を生成して返します。
	 * @return 五度圏で真裏にあたる調
	 */
	public Key oppositeKey() {
		return new Key(Music.oppositeCo5(co5), majorMinor);
	}
	/**
	 * この調の文字列表現を C、Am のような形式で返します。
	 * @return この調の文字列表現
	 */
	@Override
	public String toString() {
		return toStringIn(SymbolLanguage.SYMBOL);
	}
	/**
	 * この調の文字列表現を、指定された形式で返します。
	 * @return この調の文字列表現
	 */
	public String toStringIn(SymbolLanguage language) {
		NoteSymbol note = new NoteSymbol(co5);
		String major = note.toStringIn(language, false) + language.major;
		if( majorMinor > 0 ) {
			return major;
		}
		else {
			String minor = note.toStringIn(language, true) + language.minor;
			return majorMinor < 0 ?
				minor : major + language.majorMinorDelimiter + minor ;
		}
	}
	/**
	 * 調号を表す半角文字列を返します。
	 * 正規化された状態において最大２文字になるよう調整されます。
	 *
	 * @return 調号を表す半角文字列
	 */
	public String signature() {
		switch(co5) {
		case  0: return "==";
		case  1: return "#";
		case -1: return "b";
		case  2: return "##";
		case -2: return "bb";
		default:
			if( co5 >= 3 && co5 <= 7 ) return co5 + "#" ;
			else if( co5 <= -3 && co5 >= -7 ) return (-co5) + "b" ;
			return "";
		}
	}
	/**
	 * 調号の説明（英語）を返します。
	 * @return 調号の説明
	 */
	public String signatureDescription() {
		switch(co5) {
		case  0: return "no sharps or flats";
		case  1: return "1 sharp";
		case -1: return "1 flat";
		default: return co5 < 0 ? (-co5) + " flats" : co5 + " sharps" ;
		}
	}
}