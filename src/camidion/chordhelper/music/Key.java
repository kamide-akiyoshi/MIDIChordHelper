package camidion.chordhelper.music;


/**
 * 調（キー）を表すクラスです。値は不変です。
 *
 * <p>内部的には次の値を持っています。</p>
 * <ul>
 * <li>五度圏インデックス値。これは調号の♯の数（♭の数は負数）と同じです。</li>
 * <li>メジャー／マイナーの区別、区別なしの３値（{@link MajorMinor}で定義）</li>
 * </ul>
 * <p>これらの値はMIDIのメタメッセージにある調号のパラメータに対応します。
 * </p>
 */
public class Key {
	/**
	 * 調号の♯または♭の最大数
	 */
	public static final int MAX_SHARPS_OR_FLATS = 7;
	/**
	 * キー指定（メジャー／マイナー／両方）
	 */
	public enum MajorMinor {
		/**
		 * マイナーキー（短調）
		 */
		MINOR(-1) {
			@Override
			public MajorMinor opposite() { return MAJOR; }
		},
		/**
		 * メジャーまたはマイナー（区別なし）
		 */
		MAJOR_OR_MINOR(0) {
			@Override
			public boolean includes(MajorMinor mm) { return true; }
		},
		/**
		 * メジャーキー（長調）
		 */
		MAJOR(1) {
			@Override
			public MajorMinor opposite() { return MINOR; }
		};
		private int index;
		private MajorMinor(int index) { this.index = index; }
		/** インデックス値（マイナー：-1、区別なし：0、メジャー：1）を返します。 */
		public int index() { return index; }
		/** 反対の調を返します。 */
		public MajorMinor opposite() { return this; }
		/** この値が、引数で指定されたメジャー／マイナーを含んだ意味であるときにtrueを返します。 */
		public boolean includes(MajorMinor mm) { return equals(mm); }
	}
	/**
	 * この調の五度圏インデックス値
	 */
	private int co5;
	/**
	 * メジャー・マイナーの区別
	 */
	private MajorMinor majorMinor = MajorMinor.MAJOR_OR_MINOR;
	/**
	 * 調号が空（C/Am ハ長調またはイ短調）で、メジャー・マイナーの区別のない調を構築します。
	 */
	public Key() { }
	/**
	 * 指定の五度圏インデックス値を持つ、メジャー・マイナーの区別のない調を構築します。
	 *
	 * @param co5 五度圏インデックス値
	 */
	public Key(int co5) { this.co5 = co5; normalize(); }
	/**
	 * 指定の五度圏インデックス値を持つ、メジャー・マイナーの区別を指定した調を構築します。
	 *
	 * @param co5 五度圏インデックス値
	 * @param majorMinor メジャー・マイナーの区別
	 */
	public Key(int co5, MajorMinor majorMinor) {
		this.co5 = co5;
		this.majorMinor = majorMinor;
		normalize();
	}
	/**
	 * MIDIの調データ（メタメッセージ2byteの配列）から調を構築します。
	 * <ul>
	 * <li>長さ0の配列が与えられた場合は{@link #Key() 引数のないコンストラクタ}と同じ動作になります。</li>
	 * <li>メジャー・マイナーの区別を表す有効な値が見つからなかった場合、区別なしとして構築されます。</li>
	 * </ul>
	 *
	 * @param midiData MIDIの調データ
	 */
	public Key(byte midiData[]) {
		if( midiData.length == 0 ) return;
		co5 = midiData[0];
		if( midiData.length > 1 ) {
			switch(midiData[1]) {
			case 0 : majorMinor = MajorMinor.MAJOR; break;
			case 1 : majorMinor = MajorMinor.MINOR; break;
			}
		}
		normalize();
	}
	/**
	 * C、Am のような文字列から調を構築します。
	 * mがあればマイナー、なければメジャーとなります（区別のない状態にはなりません）。
	 *
	 * @param keySymbol 調を表す文字列
	 * @throw IllegalArgumentException 引数が空文字列の場合、または音名で始まっていない場合
	 */
	public Key(String keySymbol) throws IllegalArgumentException {
		co5 = NoteSymbol.co5OfSymbol(keySymbol);
		if( keySymbol.matches(".*m") ) { majorMinor = MajorMinor.MINOR; co5 -= 3; }
		else majorMinor = MajorMinor.MAJOR;
		normalize();
	}
	/**
	 * 指定されたコードと同名、または最も近い調を構築します。
	 * <ul>
	 * <li>コード構成音に短3度があればマイナー、なければメジャーとなります（区別なしになることはありません）。</li>
	 * <li>調として存在しないコード（例：A♯）が来た場合、存在する異名同音の調（例：B♭）に置き換えられます。</li>
	 * </ul>
	 * @param chord コード（和音）
	 */
	public Key(Chord chord) {
		co5 = chord.rootNoteSymbol().toCo5();
		if( chord.isSet(Chord.Interval.MINOR) ) { majorMinor = MajorMinor.MINOR; co5 -= 3; }
		else majorMinor = MajorMinor.MAJOR;
		normalize();
	}
	/**
	 * この調を正規化します。
	 * 調が７♭～７♯の範囲に入っていない場合、
	 * その範囲に入るよう調整されます。
	 */
	private void normalize() {
		if( co5 >= -MAX_SHARPS_OR_FLATS && co5 <= MAX_SHARPS_OR_FLATS ) return;
		if( (co5 = Music.mod12(co5)) > 6 ) co5 -= Music.SEMITONES_PER_OCTAVE;
	}
	/**
	 * 五度圏インデックス値を返します。
	 * @return 五度圏インデックス値
	 */
	public int toCo5() { return co5; }
	/**
	 * メジャー・マイナーの区別を返します。
	 * @return メジャー・マイナーの区別
	 */
	public MajorMinor majorMinor() { return majorMinor; }
	@Override
	public boolean equals(Object anObject) {
		if( this == anObject ) return true;
		if( anObject instanceof Key ) {
			Key another = (Key) anObject;
			return co5 == another.toCo5() && majorMinor == another.majorMinor();
		}
		return false;
	}
	@Override
	public int hashCode() { return 0x4000 * majorMinor.index() + co5 ; }
	/**
	 * この調の文字列表現を C、Am のような形式で返します。
	 * メジャー・マイナーの区別がない場合、調号を先頭に付加して返します。
	 * @return この調の文字列表現
	 */
	@Override
	public String toString() {
		String s = toStringIn(NoteSymbol.Language.SYMBOL);
		if(majorMinor == MajorMinor.MAJOR_OR_MINOR) s = signature() + " : " + s;
		return s;
	}
	/**
	 * この調の文字列表現を、指定された形式で返します。
	 * @return この調の文字列表現
	 */
	public String toStringIn(NoteSymbol.Language language) {
		return language.stringOf(this);
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
	/**
	 * MIDIの調データ（メタメッセージ2byte）を生成して返します。
	 * @return  MIDIの調データ
	 */
	public byte[] getBytes() {
		return new byte[] {(byte) co5, (byte) ((majorMinor == MajorMinor.MINOR) ? 1 : 0)};
	}
	/**
	 * 相対ドの音階を返します。
	 * @return 相対ドの音階（0～11）
	 */
	public int relativeDo() { return NoteSymbol.majorCo5ToNoteNumber(co5); }
	/**
	 * この調のルート音を表すノート番号（オクターブ抜き）を返します。
	 * メジャーキーの場合は相対ド、
	 * マイナーキーの場合は相対ラの音階です。
	 *
	 * @return キーのルート音（0～11）
	 */
	public int rootNoteNumber() {
		int n = relativeDo();
		return majorMinor==MajorMinor.MINOR ? Music.mod12(n-3) : n;
	}
	/**
	 * 指定されたノート番号の音が、この調のスケールの構成音か調べます。
	 * メジャーキーの場合はメジャースケール、
	 * マイナーキーの場合はナチュラルマイナースケールとして判断されます。
	 *
	 * @param noteNumber ノート番号
	 * @return 指定されたノート番号がこのキーのスケールの構成音ならtrue
	 */
	public boolean isOnScale(int noteNumber) { return Music.isOnScale(noteNumber, co5); }
	/**
	 * この調に対する平行調を返します。
	 * これは元の調と同じ調号を持つ、メジャーとマイナーが異なる調です。
	 * メジャーとマイナーの区別が不明な場合、この調自身を返します。
	 * @return 平行調
	 */
	public Key relativeKey() {
		MajorMinor mmo = majorMinor.opposite();
		return mmo.equals(majorMinor) ? this : new Key(co5, mmo);
	}
	/**
	 * この調に対する同主調を返します。
	 * これは元の調とルート音が同じで、メジャーとマイナーが異なる調です。
	 * メジャーとマイナーの区別が不明な場合、この調自身を返します。
	 *
	 * @return 同主調
	 */
	public Key parallelKey() {
		MajorMinor mmo = majorMinor.opposite();
		return mmo.equals(majorMinor) ? this : new Key(co5 - 3 * majorMinor.index(), mmo);
	}
	/**
	 * この調に異名同音の調があれば、それを返します。
	 * <p>例えば、♭５個（D♭メジャー）の場合、異名同音の調は♯７個（C♯メジャー）となります。
	 * 異名同音の調が存在しない調（４♯～４♭）に対してこのメソッドを呼び出した場合、
	 * この調自身を返します。
	 * </p>
	 * @return 異名同音の調
	 */
	public Key enharmonicKey() {
		int newCo5 = co5;
		if( newCo5 > 4 ) newCo5 -= 12; else if( newCo5 < -4 ) newCo5 += 12;
		return newCo5 == co5 ? this : new Key(newCo5, majorMinor);
	}
	/**
	 * 指定された半音オフセット値だけ移調した調を返します。
	 * 半音オフセット値が 0 の場合、この調自身を返します。
	 *
	 * @param chromaticOffset 半音オフセット値
	 * @return 移調した調
	 */
	public Key transposedKey(int chromaticOffset) {
		return chromaticOffset == 0 ? this : new Key(Music.transposeCo5(co5, chromaticOffset), majorMinor);
	}
	/**
	 * 五度圏で真裏にあたる調を返します。
	 * @return 五度圏で真裏にあたる調
	 */
	public Key createOppositeKey() {
		return new Key(Music.oppositeCo5(co5), majorMinor);
	}
}
