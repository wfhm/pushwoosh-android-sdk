/*
 *
 * Copyright (c) 2017. Pushwoosh Inc. (http://www.pushwoosh.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * (i) the original and/or modified Software should be used exclusively to work with Pushwoosh services,
 *
 * (ii) the above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.pushwoosh.thirdpart.com.ironz.binaryprefs.serialization.serializer;

/**
 * Short to byte array implementation and backwards
 */
public final class ShortSerializer {

	/**
	 * Uses for detecting byte array primitive type of {@link Short}
	 */
	private static final byte FLAG = -9;

	/**
	 * Minimum size primitive type of {@link Short}
	 */
	private static final int SIZE = 3;

	/**
	 * Serialize {@code short} into byte array with following scheme:
	 * [{@link #FLAG}] + [short_bytes].
	 *
	 * @param value target short to serialize.
	 * @return specific byte array with scheme.
	 */
	public byte[] serialize(short value) {
		return new byte[]{
				FLAG,
				(byte) (value >>> 8),
				((byte) ((short) value))
		};
	}

	/**
	 * Deserialize {@code short} by {@link #serialize(short)}  convention
	 *
	 * @param bytes target byte array for deserialization
	 * @return deserialized short
	 */
	public short deserialize(byte[] bytes) {
		return deserialize(bytes, 0);
	}

	/**
	 * Deserialize {@code short} by {@link #serialize(short)}  convention
	 *
	 * @param bytes  target byte array for deserialization
	 * @param offset bytes array offset
	 * @return deserialized short
	 */
	public short deserialize(byte[] bytes, int offset) {
		int i = 0xff;
		return (short) ((bytes[1 + offset] << 8) +
		                (bytes[2 + offset] & i));
	}

	public boolean isMatches(byte flag) {
		return flag == FLAG;
	}

	public int bytesLength() {
		return SIZE;
	}
}