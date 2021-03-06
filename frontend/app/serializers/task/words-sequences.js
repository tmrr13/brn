import BaseTaskSerializer from '../task';

export default BaseTaskSerializer.extend({
  normalize(typeClass, hash) {
    const hashCopy = {
      ...hash,
      wrongAnswers: [],
    };
    return this._super(typeClass, hashCopy);
  },
});
