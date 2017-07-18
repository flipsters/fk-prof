/**
 * Created by rohit.patiyal on 18/07/17.
 */
import React from 'react';
import styles from './WorkTypeComponent.css';

export default (props) => {
  return (

    <div

      className={`mdl-cell mdl-shadow--2dp mdl-cell--3-col mdl-cell--4-col-tablet mdl-cell--4-col-phone ${props.isDisabled? styles.disabledDiv + " " + styles.comingSoon: ""}`}>
      {/*<img src={coming_soon} className={}/>*/}
      <div className="mdl-card__title">
        <h2 className="mdl-card__title-text">{props.name}</h2>
      </div>
      <div className="mdl-grid">
        <div className="mdl-cell mdl-cell--12-col">
          {props.attributes[0]}
          <div className="mdl-textfield mdl-js-textfield">
            <input className="mdl-textfield__input" type="text" pattern="^[5-9][0-9]$|^100$"
                   id={props.name + " " + props.attributes[0]}/>
            <label className="mdl-textfield__label" htmlFor={props.name + " " + props.attributes[0]}>50</label>
            <span className="mdl-textfield__error">Input should be between 50-100!</span>
          </div>
        </div>
        <div className="mdl-cell mdl-cell--12-col">
          {props.attributes[1]}
          <div className="mdl-textfield mdl-js-textfield">
            <input className="mdl-textfield__input" type="text"
                   pattern="^([1-9]|[1-9][0-9]|[1-9][0-9][0-9])$" id={props.name + " " + props.attributes[1]}/>
            <label className="mdl-textfield__label" htmlFor={props.name + " " + props.attributes[1]}>64</label>
            <span className="mdl-textfield__error">Input should be between 1-999!</span>
          </div>
        </div>
      </div>
    </div>)
};
