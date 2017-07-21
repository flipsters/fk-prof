/**
 * Created by rohit.patiyal on 18/07/17.
 */
import React from 'react';
import styles from './WorkTypeComponent.css';

export default (props) => {
  const first_attribute = props.attributes[0].toLowerCase().split(" ").join("_");
  const second_attribute = props.attributes[1].toLowerCase().split(" ").join("_");
  return (
    <div
      className={`mdl-cell mdl-shadow--2dp mdl-cell--3-col mdl-cell--4-col-tablet mdl-cell--4-col-phone ${props.isDisabled ? styles.disabledDiv + " " + styles.comingSoon : ""}`}>
      {/*<img src={coming_soon} className={}/>*/}
      <div className="mdl-card__title">
        <h2 className="mdl-card__title-text">{props.name}</h2>
        {console.log(props)}
        <span className="mdl-textfield__error">Input should be between 1-999!</span>
      </div>
      <div className="mdl-grid">
        <div className="mdl-cell mdl-cell--12-col" >
          <div>{props.attributes[0]}</div>
          <div className="mdl-textfield mdl-js-textfield">
            <input className="mdl-textfield__input" type="number" min="50" max="100"
                   data-w-type={props.w_type}  data-attribute={first_attribute}
                   id={props.name + " " + props.attributes[0]}
                   onChange={props.onChange} value={props.value[first_attribute]}/>
            <label className="mdl-textfield__label" htmlFor={props.name + " " + props.attributes[0]}>50</label>
            <span className="mdl-textfield__error">Input should be between 50-100</span>
          </div>
        </div>
        <div className="mdl-cell mdl-cell--12-col">
          <div>{props.attributes[1]}</div>
          <div className="mdl-textfield mdl-js-textfield">
            <input className="mdl-textfield__input" type="number" min="1" max="999" name={props.w_type}
                   data-w-type={props.w_type}  data-attribute={second_attribute}
                   id={props.name + " " + props.attributes[1]} onChange={props.onChange}
                   value={props.value[second_attribute]}
            />
            <label className="mdl-textfield__label" htmlFor={props.name + " " + props.attributes[1]}>64</label>
            <span className="mdl-textfield__error">Input should be between 1-999</span>
          </div>
        </div>
      </div>
    </div>)
};
